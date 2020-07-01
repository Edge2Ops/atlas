package org.apache.atlas.repository.search;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

public class AtlasElasticsearch {
    private final RestHighLevelClient searchClient;

    public AtlasElasticsearch() {
        searchClient = ElasticsearchClient.getClient();
    }

    public SearchResponse search(String index, String queryString, int limit, int offset) throws IOException {
        SearchRequest searchRequest = new SearchRequest("janusgraph_vertex_index");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.queryStringQuery(queryString));
        sourceBuilder.from(offset);
        sourceBuilder.size(limit);
        String[] includeFields = new String[] {"__guid"};
        String[] excludeFields = new String[] {"_type"};
        sourceBuilder.fetchSource(includeFields, excludeFields);

        searchRequest.source(sourceBuilder);
        return searchClient.search(searchRequest);
    }
}
