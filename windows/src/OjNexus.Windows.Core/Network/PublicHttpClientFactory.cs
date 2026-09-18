using System.Net.Http;

namespace OjNexus.Windows.Core.Network;

/// <summary>Creates clients for anonymous public judge endpoints.</summary>
public static class PublicHttpClientFactory
{
    public static HttpClient Create() => new(CreateHandler());

    public static HttpClientHandler CreateHandler() => new()
    {
        UseCookies = false,
    };
}
