<?php
// Enable error reporting during development (disable in production)
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

// Start PHP session to store the state and Minecraft link info.
session_start();

// Replace these with your actual Discord app credentials.
$clientID = '1204206372271427675';
$clientSecret = 'sZz7A5nIycWow18b8Mrra8SeX5vVFJ0I';

// This redirect URI must match the one configured in your Discord Developer Portal.
$redirectURI = 'https://earthpol.com/linking/link.php';

// INITIAL STAGE: The user is sent here from the Minecraft plugin.
// Expect parameters: uuid, mc_code, and mc_username.
if (!isset($_GET['state'])) {
    if (!isset($_GET['uuid']) || !isset($_GET['mc_code']) || !isset($_GET['mc_username'])) {
        die("Missing required parameters. (Expected: uuid, mc_code, and mc_username)");
    }

    // Store the Minecraft UUID, linking code, and username in the session.
    $_SESSION['minecraft_uuid'] = $_GET['uuid'];
    $_SESSION['minecraft_code'] = $_GET['mc_code'];
    $_SESSION['minecraft_username'] = $_GET['mc_username'];

    // Generate a random state parameter for OAuth to prevent CSRF.
    $state = bin2hex(random_bytes(16));
    $_SESSION['oauth_state'] = $state;

    // Optionally, set a cookie with the state (expires in 5 minutes).
    setcookie("oauth_state", $state, time() + 300, "/", "", true, true);

    // Build the Discord OAuth2 URL.
    $params = [
        'client_id'     => $clientID,
        'redirect_uri'  => $redirectURI,
        'response_type' => 'code',
        'scope'         => 'identify', // Add additional scopes if needed.
        'state'         => $state,
    ];
    $oauthURL = "https://discord.com/api/oauth2/authorize?" . http_build_query($params);

    // Redirect the user to Discord's OAuth2 authorization page.
    header("Location: " . $oauthURL);
    exit();
} else {
    // CALLBACK STAGE: The user is returning from Discord with 'code' and 'state'.

    // Verify that the returned state matches the stored state.
    if (!isset($_GET['state']) || $_GET['state'] !== $_SESSION['oauth_state']) {
        die("Invalid OAuth state. Please try again.");
    }

    // Exchange the Discord OAuth code for an access token.
    $code = $_GET['code'];
    $tokenURL = "https://discord.com/api/oauth2/token";
    $data = [
        "client_id"     => $clientID,
        "client_secret" => $clientSecret,
        "grant_type"    => "authorization_code",
        "code"          => $code,
        "redirect_uri"  => $redirectURI,
    ];

    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $tokenURL);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query($data));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/x-www-form-urlencoded']);
    $result = curl_exec($ch);
    curl_close($ch);

    $tokenData = json_decode($result, true);
    if (!isset($tokenData['access_token'])) {
        die("Failed to obtain access token from Discord.");
    }
    $accessToken = $tokenData['access_token'];

    // Use the access token to fetch Discord user information.
    $ch = curl_init("https://discord.com/api/users/@me");
    curl_setopt($ch, CURLOPT_HTTPHEADER, ["Authorization: Bearer " . $accessToken]);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    $userResult = curl_exec($ch);
    curl_close($ch);

    $userData = json_decode($userResult, true);
    if (!isset($userData['id'])) {
        die("Failed to retrieve Discord user information.");
    }

    // Extract the Discord user ID and the username#discriminator.
    $discordID = $userData['id'];
    $discordUsername = $userData['username'] . "#" . $userData['discriminator'];

    // Retrieve the Minecraft info from the session.
    $minecraftUUID = $_SESSION['minecraft_uuid'];
    $minecraftCode = $_SESSION['minecraft_code'];
    $minecraftUsername = $_SESSION['minecraft_username'];

    // --- DATABASE PART ---
    // Database credentials (update these to match your environment).
    $dbHost = '10.0.0.2';
    $dbName = 'discord';
    $dbUser = 'betterdiscordsrv';
    $dbPass = 'xvNARjClG72cClCS!';

    try {
        $pdo = new PDO("mysql:host=$dbHost;dbname=$dbName;charset=utf8mb4", $dbUser, $dbPass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        ]);
    } catch (PDOException $e) {
        die("Database connection failed: " . $e->getMessage());
    }

    // Verify that there is a valid linking code for this Minecraft account.
    $stmt = $pdo->prepare("SELECT * FROM discord_codes WHERE code = ? AND uuid = ? AND expiration > ?");
    $stmt->execute([$minecraftCode, $minecraftUUID, time()]);
    $codeRow = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$codeRow) {
        die("Invalid linking code or the code does not match the Minecraft account.");
    }

    // Delete the used code from the discord_codes table.
    try {
        $stmt = $pdo->prepare("DELETE FROM discord_codes WHERE code = ? AND uuid = ?");
        $stmt->execute([$minecraftCode, $minecraftUUID]);
    } catch (PDOException $e) {
        die("Failed to remove linking code: " . $e->getMessage());
    }

    // Insert the linking information into the discord_accounts table.
    try {
        $stmt = $pdo->prepare("INSERT INTO discord_accounts (discord, uuid) VALUES (?, ?)");
        $stmt->execute([$discordID, $minecraftUUID]);
    } catch (PDOException $e) {
        die("Failed to insert linking data into the database: " . $e->getMessage());
    }

    // --- INSERT A NOTIFICATION ---
    // Insert a record into discord_notification so the Minecraft server can notify the user.
    try {
        $notifQuery = "INSERT INTO discord_notification (discord, uuid, mc_username, discord_username, timestamp) VALUES (?, ?, ?, ?, ?)";
        $stmt = $pdo->prepare($notifQuery);
        $stmt->execute([$discordID, $minecraftUUID, $minecraftUsername, $discordUsername, time()]);
    } catch (PDOException $e) {
        // Log the error, but don't fail the process.
        error_log("Failed to insert notification: " . $e->getMessage());
    }

    // Echo confirmation.
    echo "Successfully linked Minecraft account (" . htmlspecialchars($minecraftUUID) .
        ") with Discord account (" . htmlspecialchars($discordUsername) . ", ID: " .
        htmlspecialchars($discordID) . ").";

    // Clear session variables and remove the state cookie.
    unset($_SESSION['oauth_state'], $_SESSION['minecraft_uuid'], $_SESSION['minecraft_code'], $_SESSION['minecraft_username']);
    setcookie("oauth_state", "", time() - 3600, "/");
}
?>
