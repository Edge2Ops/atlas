package org.apache.atlas.repository.search;

import org.apache.atlas.ApplicationProperties;
import org.apache.atlas.AtlasException;
import org.apache.commons.configuration.Configuration;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchClient {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchClient.class);
    private static volatile RestHighLevelClient searchClient;

    public static final String INDEX_BACKEND_ES = "elasticsearch";
    public static final String INDEX_BACKEND_CONF = "atlas.graph.index.search.hostname=localhost:9200";

    public ElasticsearchClient() {
    }

    public String getHost() throws AtlasException {
        Configuration configuration = ApplicationProperties.get();
        return configuration.getString(INDEX_BACKEND_CONF);
    }

    public static RestHighLevelClient getClient() {
        if (searchClient == null) {
            synchronized (ElasticsearchClient.class) {
                if (searchClient == null) {
                    RestClientBuilder restClientBuilder = RestClient.builder(
                            new HttpHost("localhost", 9200, "http"));
                    searchClient =
                            new RestHighLevelClient(restClientBuilder);
                }
            }
        }
        return searchClient;
    }

}
