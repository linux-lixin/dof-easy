using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;

namespace DNFLogin
{
    internal static class LoginConfig
    {
        private const string DefaultWindowTitle = "地下城与勇士V1.0";
        private const string DefaultGameVersion = "1.0.0";
        private const string LoginSection = "Login";
        private const string CredentialsSection = "Credentials";
        private const string ProtectedPasswordKey = "PasswordDpapi";
        internal const string ConfigurationError = "服务器配置无效或无法读取。请在启动器目录的 login.ini 中设置 [Login] 下的 BaseUrl（http:// 或 https://），然后重新启动。未连接任何服务器。";

        // Deliberately no fallback URL or alternate configuration directory.
        private static string ConfigPath(string? path) => path ?? Path.Combine(AppContext.BaseDirectory, "login.ini");

        public static string GetBaseUrl(string? path = null)
        {
            try
            {
                var ini = IniDocument.Load(ConfigPath(path));
                if (IsValidUrl(ini.Get(LoginSection, "BaseUrl"), out var normalized))
                    return normalized;
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
            {
                // Never select another server or create a file when this file cannot be read.
            }
            throw new InvalidOperationException(ConfigurationError);
        }

        public static string GetWindowTitle(string? path = null) => ReadSetting("WindowTitle", DefaultWindowTitle, path);
        public static string GetGameVersion(string? path = null) => ReadSetting("GameVersion", DefaultGameVersion, path);

        private static string ReadSetting(string key, string fallback, string? path)
        {
            try
            {
                var value = IniDocument.Load(ConfigPath(path)).Get(LoginSection, key);
                return string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
            {
                return fallback;
            }
        }

        public static void SetGameVersion(string? version, string? path = null)
        {
            GetBaseUrl(path);
            var ini = IniDocument.Load(ConfigPath(path));
            ini.Set(LoginSection, "GameVersion", string.IsNullOrWhiteSpace(version) ? DefaultGameVersion : version.Trim());
            ini.WriteTo(ConfigPath(path));
        }

        public static LoginCredentials GetSavedCredentials(string? path = null)
        {
            try
            {
                var baseUrl = GetBaseUrl(path);
                var ini = IniDocument.Load(ConfigPath(path));
                var account = ini.Get(CredentialsSection, "Account") ?? string.Empty;
                // Never return legacy plaintext or use it in a request. Require manual entry.
                if (ini.Get(CredentialsSection, "Password") is not null)
                {
                    ini.Set(CredentialsSection, "Password", null);
                    ini.Set(CredentialsSection, ProtectedPasswordKey, null);
                    ini.Set(CredentialsSection, "RememberPassword", bool.FalseString);
                    ini.WriteTo(ConfigPath(path));
                    return new LoginCredentials(account, string.Empty, false);
                }
                if (!bool.TryParse(ini.Get(CredentialsSection, "RememberPassword"), out var remember) || !remember)
                    return new LoginCredentials(account, string.Empty, false);
                var encoded = ini.Get(CredentialsSection, ProtectedPasswordKey);
                if (string.IsNullOrWhiteSpace(encoded))
                    return new LoginCredentials(account, string.Empty, false);

                byte[]? plaintext = null;
                try
                {
                    plaintext = ProtectedData.Unprotect(Convert.FromBase64String(encoded), Entropy(baseUrl), DataProtectionScope.CurrentUser);
                    return new LoginCredentials(account, Encoding.UTF8.GetString(plaintext), true);
                }
                catch (Exception ex) when (ex is CryptographicException or FormatException)
                {
                    // Corrupt, copied, or server-mismatched credentials require manual entry.
                    return new LoginCredentials(account, string.Empty, false);
                }
                finally
                {
                    if (plaintext is not null) CryptographicOperations.ZeroMemory(plaintext);
                }
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidOperationException or ArgumentException)
            {
                return LoginCredentials.Empty;
            }
        }

        public static void SaveCredentials(LoginCredentials credentials, string? path = null)
        {
            var baseUrl = GetBaseUrl(path);
            var ini = IniDocument.Load(ConfigPath(path));
            var remember = credentials.RememberPassword && !string.IsNullOrEmpty(credentials.Password);
            string? encrypted = null;
            if (remember)
            {
                var plaintext = Encoding.UTF8.GetBytes(credentials.Password!);
                try
                {
                    encrypted = Convert.ToBase64String(ProtectedData.Protect(plaintext, Entropy(baseUrl), DataProtectionScope.CurrentUser));
                }
                finally
                {
                    CryptographicOperations.ZeroMemory(plaintext);
                }
            }
            ini.Set(CredentialsSection, "Password", null);
            ini.Set(CredentialsSection, ProtectedPasswordKey, encrypted);
            ini.Set(CredentialsSection, "Account", remember ? credentials.Account ?? string.Empty : null);
            ini.Set(CredentialsSection, "RememberPassword", remember.ToString());
            ini.WriteTo(ConfigPath(path));
        }

        private static byte[] Entropy(string baseUrl) => Encoding.UTF8.GetBytes("DNFLogin.Password.v1|" + baseUrl);

        private static bool IsValidUrl(string? value, out string normalized)
        {
            normalized = string.Empty;
            if (string.IsNullOrWhiteSpace(value)) return false;
            var candidate = value.Trim();
            if (candidate.Any(char.IsWhiteSpace) || candidate.Contains('\\') ||
                !Uri.TryCreate(candidate, UriKind.Absolute, out var uri) ||
                (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps) ||
                string.IsNullOrWhiteSpace(uri.Host) || !string.IsNullOrEmpty(uri.UserInfo) ||
                !string.IsNullOrEmpty(uri.Query) || !string.IsNullOrEmpty(uri.Fragment))
                return false;
            normalized = uri.AbsoluteUri.TrimEnd('/');
            return true;
        }

        // Edit only requested keys, retaining comments, ordering and unrelated settings.
        private sealed class IniDocument
        {
            private readonly List<string> lines;
            private IniDocument(List<string> lines) => this.lines = lines;
            public static IniDocument Load(string path) => new(File.ReadAllLines(path, new UTF8Encoding(false, true)).ToList());

            private List<int> FindKeys(string section, string key)
            {
                var result = new List<int>();
                var currentSection = string.Empty;
                for (var i = 0; i < lines.Count; i++)
                {
                    var line = lines[i].Trim();
                    if (line.StartsWith('[') && line.EndsWith(']'))
                        currentSection = line[1..^1].Trim();
                    else if (string.Equals(section, currentSection, StringComparison.OrdinalIgnoreCase) &&
                             !line.StartsWith(';') && !line.StartsWith('#'))
                    {
                        var separator = line.IndexOf('=');
                        if (separator > 0 && string.Equals(line[..separator].Trim(), key, StringComparison.OrdinalIgnoreCase))
                            result.Add(i);
                    }
                }
                return result;
            }

            public string? Get(string section, string key)
            {
                var matches = FindKeys(section, key);
                if (matches.Count == 0) return null;
                if (key.Equals("BaseUrl", StringComparison.OrdinalIgnoreCase) && matches.Count != 1) return null;
                var line = lines[matches[^1]];
                return line[(line.IndexOf('=') + 1)..].Trim();
            }

            public void Set(string section, string key, string? value)
            {
                if (value is not null && (value.Contains('\r') || value.Contains('\n')))
                    throw new ArgumentException("配置值不能包含换行。");
                var matches = FindKeys(section, key);
                if (matches.Count > 0)
                {
                    for (var i = matches.Count - 1; i >= 0; i--)
                    {
                        if (i == 0 && value is not null) lines[matches[i]] = $"{key}={value}";
                        else lines.RemoveAt(matches[i]);
                    }
                    return;
                }
                if (value is null) return;
                var header = lines.FindIndex(line => string.Equals(line.Trim(), $"[{section}]", StringComparison.OrdinalIgnoreCase));
                if (header < 0)
                {
                    lines.Add($"[{section}]");
                    lines.Add($"{key}={value}");
                }
                else lines.Insert(header + 1, $"{key}={value}");
            }

            public void WriteTo(string path)
            {
                var temporaryPath = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
                try
                {
                    File.WriteAllLines(temporaryPath, lines, new UTF8Encoding(false));
                    File.Move(temporaryPath, path, true);
                }
                finally
                {
                    if (File.Exists(temporaryPath)) File.Delete(temporaryPath);
                }
            }
        }

        public sealed record LoginCredentials(string? Account, string? Password, bool RememberPassword)
        {
            public static LoginCredentials Empty { get; } = new(string.Empty, string.Empty, false);
        }
    }
}
