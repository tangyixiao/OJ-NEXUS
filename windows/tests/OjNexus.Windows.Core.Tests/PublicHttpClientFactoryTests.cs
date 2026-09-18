using OjNexus.Windows.Core.Network;

namespace OjNexus.Windows.Core.Tests;

public sealed class PublicHttpClientFactoryTests
{
    [Fact]
    public void CreateHandler_DisablesCookieHandling()
    {
        using var handler = PublicHttpClientFactory.CreateHandler();

        Assert.False(handler.UseCookies);
    }
}
