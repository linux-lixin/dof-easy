using System;
using System.Diagnostics.Tracing;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using DNFLogin;

internal static class Program
{
    [STAThread]
    private static int Main()
    {
        var application = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
        var path = Path.Combine(AppContext.BaseDirectory, "login.ini");
        if (File.Exists(path)) throw new InvalidOperationException("Refusing to overwrite an existing test configuration.");
        using var http = new HttpRequests();
        var checks = 0;
        try
        {
            foreach (var config in new string?[] { null, "[Login]\nBaseUrl=not-a-server\n[Custom]\nKeep=yes\n" })
            {
                if (config is not null) File.WriteAllText(path, config);
                foreach (Window window in new Window[] { new MainWindow(), new LoginWindow(), new RegisterWindow() })
                {
                    try
                    {
                        // Exercise the real XAML-wired event without displaying test windows.
                        window.RaiseEvent(new RoutedEventArgs(FrameworkElement.LoadedEvent));
                        var textName = window is MainWindow ? "StepDetailText" : window is LoginWindow ? "LoginStatusText" : "StatusBodyText";
                        var detail = (TextBlock)window.FindName(textName);
                        if (!detail.Text.Contains("login.ini") || !detail.Text.Contains("未连接任何服务器"))
                            throw new Exception(window.GetType().Name + " did not show the friendly configuration error.");
                        if (window is LoginWindow && ((Button)window.FindName("LoginButton")).IsEnabled)
                            throw new Exception("Login remains enabled with an invalid configuration.");
                        if (window is RegisterWindow && ((Button)window.FindName("RegisterSubmitButton")).IsEnabled)
                            throw new Exception("Registration remains enabled with an invalid configuration.");
                        checks++;
                    }
                    finally { window.Close(); }
                }
                if (http.RequestCount != 0) throw new Exception("A blocked window initiated an HTTP request.");
                if (config is null ? File.Exists(path) : File.ReadAllText(path) != config)
                    throw new Exception("A blocked window modified configuration.");
            }
            Console.WriteLine($"PASS: {checks} real WPF window checks; zero HTTP requests; configuration unchanged");
            return 0;
        }
        finally
        {
            if (File.Exists(path)) File.Delete(path);
            application.Shutdown();
        }
    }

    private sealed class HttpRequests : EventListener
    {
        public int RequestCount { get; private set; }
        protected override void OnEventSourceCreated(EventSource source)
        {
            if (source.Name == "System.Net.Http") EnableEvents(source, EventLevel.Verbose);
        }
        protected override void OnEventWritten(EventWrittenEventArgs data)
        {
            if (data.EventName == "RequestStart") RequestCount++;
        }
    }
}
