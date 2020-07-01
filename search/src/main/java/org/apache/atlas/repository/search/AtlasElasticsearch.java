package org.apache.atlas.repository.search;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;

public class AtlasElasticsearch {
    private final RestHighLevelClient searchClient;

    public AtlasElasticsearch() {
        searchClient = ElasticsearchClient.getClient();
    }

    public SearchResponse search(String index, SearchSourceBuilder sourceBuilder, int limit, int offset) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        sourceBuilder.sort(new ScoreSortBuilder().order(SortOrder.DESC));
        sourceBuilder.from(offset);
        sourceBuilder.size(limit);
        searchRequest.source(sourceBuilder);
        return searchClient.search(searchRequest);
    }
}
