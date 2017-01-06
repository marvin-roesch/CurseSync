package de.mineformers.cursesync.util;

import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.TextUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class SafeRedirectStrategy extends DefaultRedirectStrategy
{
    public static final RedirectStrategy INSTANCE = new SafeRedirectStrategy();

    private static String encode(String input)
    {
        StringBuilder resultStr = new StringBuilder();
        for (char ch : input.toCharArray())
        {
            if (isUnsafe(ch))
            {
                resultStr.append('%');
                resultStr.append(toHex(ch / 16));
                resultStr.append(toHex(ch % 16));
            }
            else
            {
                resultStr.append(ch);
            }
        }
        return resultStr.toString();
    }

    private static char toHex(int ch)
    {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch)
    {
        return ch > 128 || " $+,;@<>[]".indexOf(ch) >= 0;
    }

    @Override
    protected URI createLocationURI(String location) throws ProtocolException
    {
        try
        {
            final URIBuilder b = new URIBuilder(new URI(encode(location)).normalize());
            final String host = b.getHost();
            if (host != null)
            {
                b.setHost(host.toLowerCase(Locale.ROOT));
            }
            final String path = b.getPath();
            if (TextUtils.isEmpty(path))
            {
                b.setPath("/");
            }
            return b.build();
        }
        catch (final URISyntaxException ex)
        {
            throw new ProtocolException("Invalid redirect URI: " + location, ex);
        }
    }
}
