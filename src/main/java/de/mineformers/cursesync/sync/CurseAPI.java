package de.mineformers.cursesync.sync;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import de.mineformers.cursesync.sync.model.CurseProject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessageFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static de.mineformers.cursesync.CurseSync.GSON;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class CurseAPI
{
    private static final String PROTOCOL = "https";
    private static final String CURSEFORGE_URL = "minecraft.curseforge.com";
    private static final String MCF_URL = "widget.mcf.li";
    private static final String SEARCH_PATH = "/search/get-results";
    private static final String SEARCH_QUERY = "providerIdent=projects&search=%s";
    private static final String PACK_PATH = "/modpacks/minecraft/%s.json";
    private static final String PROJECT_PATH = "/projects/%d";
    private final Cache<SearchRequest, List<SearchResult>> searches = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
    private final Cache<String, CurseProject> modpacks = CacheBuilder.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private final Cache<Integer, String> modSlugs = CacheBuilder.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    @Inject
    private Logger log;
    @Inject
    private HttpClient http;

    public void init()
    {
        try
        {
            log.info("Initializing HTTP client...");
            // Initialise HTTP client such that we get cookies.
            http.execute(new HttpGet(getURI(CURSEFORGE_URL, "/search", null)));
            log.info("Done.");
        }
        catch (Exception e)
        {
            log.error("Failed to initialize HTTP client.", e);
            System.exit(1);
        }
    }

    @Nonnull
    public URI getCFURI(@Nullable String path, @Nullable String query) throws URISyntaxException
    {
        return getURI(CURSEFORGE_URL, path, query);
    }

    @Nonnull
    public URI getURI(String host, @Nullable String path, @Nullable String query) throws URISyntaxException
    {
        return new URI(PROTOCOL, host, path, query, null);
    }

    @Nullable
    public String getModSlug(int id)
    {
        try
        {
            log.debug("Getting mod slug for id {}.", id);
            return modSlugs.get(id, () ->
            {
                log.debug("Mod not cached yet, performing request.");
                return getModSlug0(id);
            });
        }
        catch (ExecutionException e)
        {
            log.error("Failed to perform request via cache. Restarting direct request...", e);
            return getModSlug0(id);
        }
    }

    @Nullable
    private String getModSlug0(int id)
    {
        try
        {
            log.debug("Getting mod slug from server...");
            URI uri = getURI(CURSEFORGE_URL, String.format(PROJECT_PATH, id), null);
            RequestConfig requestConfig = RequestConfig.custom()
                    .setRedirectsEnabled(false)
                    .build();
            HttpGet request = new HttpGet(uri.toURL().toString());
            request.setConfig(requestConfig);
            HttpResponse response = http.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 301 || statusCode == 302)
            {
                Splitter splitter = Splitter.on('/').omitEmptyStrings();
                List<String> pathParts = splitter.splitToList(response.getFirstHeader("Location").getValue());
                return pathParts.get(pathParts.size() - 1);
            }
            return null;
        }
        catch (Exception e)
        {
            log.error("Failed to perform request from CurseForge site.", e);
            return null;
        }
    }

    @Nullable
    public CurseProject getModpack(@Nonnull String slug)
    {
        try
        {
            log.debug("Getting modpack with slug {}.", slug);
            return modpacks.get(slug, () ->
            {
                log.debug("Modpack not cached yet, performing request.");
                return getModpack0(slug);
            });
        }
        catch (ExecutionException e)
        {
            log.error("Failed to perform request via cache. Restarting direct request...", e);
            return getModpack0(slug);
        }
    }

    @Nullable
    private CurseProject getModpack0(@Nonnull String slug)
    {
        try
        {
            log.debug("Getting modpack from server...");
            URI uri = getURI(MCF_URL, String.format(PACK_PATH, slug), null);
            HttpUriRequest request = new HttpGet(uri.toURL().toString());
            HttpResponse response = http.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return GSON.fromJson(json, CurseProject.class);
        }
        catch (Exception e)
        {
            log.error("Failed to perform request from MCF Widget API.", e);
            return null;
        }
    }

    @Nonnull
    public List<SearchResult> search(String category, @Nullable String term, int limit)
    {
        if (Strings.isNullOrEmpty(term))
            return ImmutableList.of();
        SearchRequest request = new SearchRequest(category, term);
        try
        {
            log.debug("Searching for '{}' in '{}', limiting results to {} entries.", term, category, limit);
            return searches.get(request, () ->
            {
                log.debug("Search not cached yet, performing request.");
                return search0(category, term);
            }).stream().limit(limit).collect(toList());
        }
        catch (ExecutionException e)
        {
            log.error("Failed to perform search via cache. Restarting direct request...", e);
            return search0(category, term).stream().limit(limit).collect(toList());
        }
    }

    private List<SearchResult> search0(String category, String term)
    {
        try
        {
            log.debug("Getting search results from server...");
            URI uri = getURI(CURSEFORGE_URL, SEARCH_PATH, String.format(SEARCH_QUERY, term));
            HttpUriRequest request = new HttpGet(uri.toURL().toString());
            HttpResponse response = http.execute(request);
            String html = EntityUtils.toString(response.getEntity());
            Document doc = Jsoup.parse(html);
            ImmutableList.Builder<SearchResult> results = new ImmutableList.Builder<>();
            Elements rows = doc.select(".listing tbody tr.results");
            for (Element row : rows)
            {
                URL url = new URL(PROTOCOL + "://" + CURSEFORGE_URL + row.select(".results-name a").attr("href"));
                Map<String, List<String>> query = splitQuery(url);
                if (!query.get("gameCategorySlug").get(0).equals(category))
                {
                    continue;
                }
                String projectSlug = String.format("%s-%s", query.get("projectID").get(0), url.getPath().split("/")[2]);
                String name = row.select(".results-name a").text();
                String description = row.select(".results-summary").text();
                String lastUpdated = row.select(".results-date").text();
                String thumbnail = row.select(".results-image img").attr("src");
                results.add(new SearchResult(projectSlug, name, description, lastUpdated, thumbnail));
            }
            List<SearchResult> result = results.build();
            log.debug("Retrieved {} search results for '{}' in '{}'.", result.size(), term, category);
            return result;
        }
        catch (Exception e)
        {
            log.error("Failed to perform search on Curse site.", e);
            return ImmutableList.of();
        }
    }

    public Map<String, List<String>> splitQuery(URL url)
    {
        if (Strings.isNullOrEmpty(url.getQuery()))
        {
            return Collections.emptyMap();
        }
        return Arrays.stream(url.getQuery().split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(AbstractMap.SimpleImmutableEntry::getKey, LinkedHashMap::new, mapping(Map.Entry::getValue, toList())));
    }

    public AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it)
    {
        final int idx = it.indexOf("=");
        final String key = idx > 0 ? it.substring(0, idx) : it;
        final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    public boolean downloadFile(URI url, File destination, int trials)
    {
        log.info("Downloading '{}' to '{}'...", url, destination.getAbsolutePath());
        if (destination.exists())
        {
            log.info("File already exists, assuming equivalence and skipping download...");
            return true;
        }
        File parent = destination.getParentFile();
        if (!parent.exists() && !destination.getParentFile().mkdirs())
        {
            log.error("Failed to create required directories, cancelling download.");
            return false;
        }
        for (int trial = 1; trial <= trials; trial++)
        {
            try
            {
                HttpUriRequest request = new HttpGet(url.toURL().toString());
                HttpResponse response = http.execute(request);
                if (response.getStatusLine().getStatusCode() == 404)
                {
                    log.error("'{}' could not be found on the server, cancelling download.", url);
                    return false;
                }
                BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent());
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination));
                int inByte;
                while ((inByte = bis.read()) != -1) bos.write(inByte);
                bis.close();
                bos.close();
                log.info("Successfully downloaded file to '{}'", destination.getAbsolutePath());
                return true;
            }
            catch (Exception e)
            {
                log.error(new FormattedMessageFactory().newMessage("Failed to download file, starting attempt #{}.", trial), e);
            }
        }
        log.error("Failed to download file after {} attempts.", trials);
        return false;
    }

    private static class SearchRequest
    {
        final String category;
        final String term;

        private SearchRequest(String category, String term)
        {
            this.category = category;
            this.term = term;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchRequest request = (SearchRequest) o;
            return Objects.equal(category, request.category) && Objects.equal(term, request.term);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(category, term);
        }
    }

    public static class SearchResult
    {
        public final String slug;
        public final String name;
        public final String description;
        public final String lastUpdated;
        public final String thumbnail;

        public SearchResult(String slug, String name, String description, String lastUpdated, String thumbnail)
        {
            this.slug = slug;
            this.name = name;
            this.description = description;
            this.lastUpdated = lastUpdated;
            this.thumbnail = thumbnail;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchResult that = (SearchResult) o;
            return Objects.equal(slug, that.slug) && Objects.equal(lastUpdated, that.lastUpdated);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(slug, lastUpdated);
        }

        @Override
        public String toString()
        {
            return MoreObjects.toStringHelper(this)
                    .add("slug", slug)
                    .add("name", name)
                    .add("description", description)
                    .add("lastUpdated", lastUpdated)
                    .add("thumbnail", thumbnail)
                    .toString();
        }
    }
}
