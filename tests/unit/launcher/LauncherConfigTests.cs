using System;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using DNFLogin;

public static class LauncherConfigTests
{
    private static int checks;
    private static void Check(bool condition, string label)
    {
        if (!condition) throw new Exception("FAIL: " + label);
        checks++;
    }

    private static void Reject(Action action, string label)
    {
        try { action(); }
        catch (InvalidOperationException ex)
        {
            Check(ex.Message.Contains("login.ini"), label + " friendly guidance");
            return;
        }
        throw new Exception("FAIL: accepted " + label);
    }

    public static int Run(string directory)
    {
        checks = 0;
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, "login.ini");
        Reject(() => LoginConfig.GetBaseUrl(path), "missing file");
        LoginConfig.GetWindowTitle(path);
        LoginConfig.GetGameVersion(path);
        LoginConfig.GetSavedCredentials(path);
        Check(!File.Exists(path), "getters never create a missing config");

        foreach (var url in new[] { "", "relative/path", "ftp://example.test", "https://", "https://user:pass@example.test", "https://example.test/?key=x", "https://example.test/#x", "https://exa mple.test", "https:\\example.test" })
        {
            var original = "; preserve me\r\n[Login]\r\nBaseUrl=" + url + "\r\n[Custom]\r\nKey=Value\r\n";
            File.WriteAllText(path, original);
            Reject(() => LoginConfig.GetBaseUrl(path), "invalid URL " + url);
            LoginConfig.GetWindowTitle(path);
            LoginConfig.GetGameVersion(path);
            Check(LoginConfig.GetSavedCredentials(path).Password == "", "invalid config cannot supply credentials");
            Reject(() => LoginConfig.SaveCredentials(new("account", "password", true), path), "save with invalid URL");
            Reject(() => LoginConfig.SetGameVersion("2.0", path), "version write with invalid URL");
            Check(File.ReadAllText(path) == original, "invalid config is byte-preserved");
        }

        File.WriteAllText(path, "[Login]\nWindowTitle=Custom\n[Other]\nBaseUrl=https://example.test\n");
        Reject(() => LoginConfig.GetBaseUrl(path), "missing Login BaseUrl");
        File.WriteAllText(path, "[Login]\nBaseUrl=https://one.test\nBaseUrl=https://two.test\n");
        Reject(() => LoginConfig.GetBaseUrl(path), "duplicate BaseUrl");
        Reject(() => LoginConfig.GetBaseUrl(directory), "unreadable directory path");
        File.WriteAllBytes(path, new byte[] { 0xff, 0xff, 0xff });
        Reject(() => LoginConfig.GetBaseUrl(path), "invalid UTF-8 configuration");
        Check(File.ReadAllBytes(path).SequenceEqual(new byte[] { 0xff, 0xff, 0xff }), "invalid encoding preserved");

        const string originalConfig = "; retain this comment\n[Login]\nBaseUrl=http://127.0.0.1:8080/\nWindowTitle=私人服务器\nGameVersion=86.1\n[Custom]\nSecretSetting=untouched\n";
        File.WriteAllText(path, originalConfig);
        using (var locked = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            Reject(() => LoginConfig.GetBaseUrl(path), "unreadable locked file");
        Check(LoginConfig.GetBaseUrl(path) == "http://127.0.0.1:8080", "explicit local server is honored");
        Check(LoginConfig.GetWindowTitle(path) == "私人服务器", "custom title retained");
        Check(LoginConfig.GetGameVersion(path) == "86.1", "custom version retained");
        Check(File.ReadAllText(path) == originalConfig, "valid getters do not rewrite");

        const string password = "test-only-password-中文-#=42";
        LoginConfig.SaveCredentials(new("tester", password, true), path);
        var saved = File.ReadAllText(path);
        Check(!saved.Contains(password) && !saved.Split('\n').Any(line => line.StartsWith("Password=")), "no plaintext password on disk");
        Check(saved.Contains("PasswordDpapi="), "encrypted credential persisted");
        Check(saved.Contains("; retain this comment") && saved.Contains("SecretSetting=untouched") && saved.Contains("GameVersion=86.1"), "unrelated configuration retained");
        var credentials = LoginConfig.GetSavedCredentials(path);
        Check(credentials.Account == "tester" && credentials.Password == password && credentials.RememberPassword, "DPAPI round trip");

        var cipher = saved.Split('\n').Single(line => line.StartsWith("PasswordDpapi="))["PasswordDpapi=".Length..].Trim();
        var unprotected = ProtectedData.Unprotect(Convert.FromBase64String(cipher), Encoding.UTF8.GetBytes("DNFLogin.Password.v1|http://127.0.0.1:8080"), DataProtectionScope.CurrentUser);
        Check(Encoding.UTF8.GetString(unprotected) == password, "actual Windows CurrentUser DPAPI ciphertext");
        CryptographicOperations.ZeroMemory(unprotected);

        File.WriteAllText(path, saved.Replace("127.0.0.1:8080", "127.0.0.1:9090"));
        Check(LoginConfig.GetSavedCredentials(path).Password == "", "credentials bound to server URL");
        File.WriteAllText(path, saved.Replace(cipher, "this-is-not-base64"));
        Check(LoginConfig.GetSavedCredentials(path).Password == "", "corrupt ciphertext does not autofill");
        File.WriteAllText(path, saved.Replace(cipher, Convert.ToBase64String(new byte[128])));
        Check(LoginConfig.GetSavedCredentials(path).Password == "", "invalid DPAPI blob does not autofill");
        File.WriteAllText(path, saved.Replace("RememberPassword=True", "RememberPassword=False"));
        Check(LoginConfig.GetSavedCredentials(path).Password == "", "unchecked remember never decrypts");

        File.WriteAllText(path, saved + "Password=legacy-secret\npassword=second-legacy-secret\n");
        credentials = LoginConfig.GetSavedCredentials(path);
        Check(credentials.Account == "tester" && credentials.Password == "" && !credentials.RememberPassword, "legacy passwords never returned, even beside a DPAPI blob");
        var migrated = File.ReadAllText(path);
        Check(!migrated.Contains("legacy-secret") && !migrated.Contains("PasswordDpapi="), "all legacy password copies erased");
        Check(migrated.Contains("SecretSetting=untouched"), "legacy erasure retains other settings");

        LoginConfig.SaveCredentials(new("tester", password, true), path);
        LoginConfig.SaveCredentials(LoginConfig.LoginCredentials.Empty, path);
        Check(!File.ReadAllText(path).Contains("PasswordDpapi="), "unchecking remember erases persisted ciphertext");
        Check(LoginConfig.GetSavedCredentials(path).Password == "", "cleared credentials stay cleared");
        LoginConfig.SetGameVersion("86.2", path);
        Check(LoginConfig.GetGameVersion(path) == "86.2" && File.ReadAllText(path).Contains("SecretSetting=untouched"), "version updates retain custom settings");
        Check(Directory.GetFiles(directory, "*.tmp").Length == 0, "atomic writes leave no temporary credential files");
        return checks;
    }
}
