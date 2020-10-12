/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.tools;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.atlas.ApplicationProperties;
import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.AtlasException;
import org.apache.atlas.listener.TypeDefChangeListener;
import org.apache.atlas.model.SearchFilter;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.typedef.AtlasEntityDef;
import org.apache.atlas.model.typedef.AtlasStructDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.apache.atlas.repository.Constants;
import org.apache.atlas.repository.graph.AtlasGraphProvider;
import org.apache.atlas.repository.graph.GraphBackedSearchIndexer;
import org.apache.atlas.repository.graphdb.AtlasGraph;
import org.apache.atlas.repository.graphdb.AtlasGraphManagement;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.graphdb.janus.AtlasElasticsearchDatabase;
import org.apache.atlas.repository.graphdb.janus.AtlasJanusGraph;
import org.apache.atlas.repository.graphdb.janus.AtlasJanusGraphDatabase;
import org.apache.atlas.repository.graphdb.janus.AtlasJanusGraphManagement;
import org.apache.atlas.repository.store.bootstrap.AtlasTypeDefStoreInitializer;
import org.apache.atlas.repository.store.graph.v2.AtlasGraphUtilsV2;
import org.apache.atlas.repository.store.graph.v2.AtlasTypeDefGraphStoreV2;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.type.AtlasStructType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.utils.AuthenticationUtil;
import org.apache.atlas.v1.model.typedef.AttributeDefinition;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.Configuration;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.Mapping;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.ReindexRequestBuilder;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.schema.JanusGraphIndex;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.schema.SchemaAction;
import org.janusgraph.core.schema.SchemaStatus;
import org.janusgraph.diskstorage.BackendTransaction;
import org.janusgraph.diskstorage.indexing.IndexEntry;
import org.janusgraph.graphdb.database.IndexSerializer;
import org.janusgraph.graphdb.database.StandardJanusGraph;
import org.janusgraph.graphdb.database.management.ManagementSystem;
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx;
import org.janusgraph.graphdb.types.MixedIndexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class RepairIndex {
    private static final Logger LOG = LoggerFactory.getLogger(RepairIndex.class);

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILED = 1;
    private static final int MAX_TRIES_ON_FAILURE = 3;

    private static final String INDEX_NAME_VERTEX_INDEX = "vertex_index";
    private static final String INDEX_NAME_FULLTEXT_INDEX = "fulltext_index";
    private static final String INDEX_NAME_EDGE_INDEX = "edge_index";
    private static final String DEFAULT_ATLAS_URL = "http://localhost:21000/";
    private static final String APPLICATION_PROPERTY_ATLAS_ENDPOINT = "atlas.rest.address";

    private static JanusGraph graph;
    private static AtlasClientV2 atlasClientV2;
    private static boolean isSelectiveRestore;

    public static void main(String[] args) {
        int exitCode = EXIT_CODE_FAILED;
        LOG.info("Started index repair");

        try {
            CommandLine cmd = getCommandLine(args);
            String guid = cmd.getOptionValue("g");

            if (guid != null && !guid.isEmpty()) {
                isSelectiveRestore = true;
                String uid = cmd.getOptionValue("u");
                String pwd = cmd.getOptionValue("p");
                setupAtlasClient(uid, pwd);
            }

            process(guid);
            fixForStringIndexAndCustomNormalizer();

            LOG.info("Completed index repair!");
            exitCode = EXIT_CODE_SUCCESS;
        } catch (Exception e) {
            LOG.error("Failed!", e);
            display("Failed: " + e.getMessage());
        }

        System.exit(exitCode);
    }

    private static void process(String guid) throws Exception {

        RepairIndex repairIndex = new RepairIndex();

        setupGraph();

        if (isSelectiveRestore) {
            repairIndex.restoreSelective(guid);
        } else {
            deleteIndices();
            repairIndex.restoreAll();
        }

        displayCrlf("Repair Index: Done!");
    }

    private static CommandLine getCommandLine(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("g", "guid", true, "guid for which update index should be executed.");
        options.addOption("u", "user", true, "User name.");
        options.addOption("p", "password", true, "Password name.");

        return new DefaultParser().parse(options, args);
    }

    private static void setupGraph() {
        display("Initializing graph: ");
        graph = AtlasJanusGraphDatabase.getGraphInstance();
        displayCrlf("Graph Initialized!");
    }

    private static String[] getIndexes() {
        return new String[]{INDEX_NAME_VERTEX_INDEX, INDEX_NAME_EDGE_INDEX, INDEX_NAME_FULLTEXT_INDEX};
    }

    private static void setupAtlasClient(String uid, String pwd) throws AtlasException {
        String[] atlasEndpoint = getAtlasRESTUrl();
        if (atlasEndpoint == null || atlasEndpoint.length == 0) {
            atlasEndpoint = new String[]{DEFAULT_ATLAS_URL};
        }
        atlasClientV2 = getAtlasClientV2(atlasEndpoint, new String[]{uid, pwd});
    }

    private void restoreAll() throws Exception {
        for (String indexName : getIndexes()) {
            displayCrlf("Restoring: " + indexName);
            long startTime = System.currentTimeMillis();

            ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
            JanusGraphIndex index = mgmt.getGraphIndex(indexName);
            mgmt.updateIndex(index, SchemaAction.REINDEX).get();
            mgmt.commit();

            ManagementSystem.awaitGraphIndexStatus(graph, indexName).status(SchemaStatus.ENABLED).call();

            display(": Time taken: " + (System.currentTimeMillis() - startTime) + " ms");
            displayCrlf(": Done!");
        }
    }


    private void restoreSelective(String guid) throws Exception {
        Set<String> referencedGUIDs = new HashSet<>(getEntityAndReferenceGuids(guid));
        displayCrlf("processing referencedGuids => " + referencedGUIDs);

        StandardJanusGraph janusGraph = (StandardJanusGraph) graph;
        IndexSerializer indexSerializer = janusGraph.getIndexSerializer();

        for (String indexName : getIndexes()) {
            displayCrlf("Restoring: " + indexName);
            long startTime = System.currentTimeMillis();
            reindexVertex(indexName, indexSerializer, referencedGUIDs);

            display(": Time taken: " + (System.currentTimeMillis() - startTime) + " ms");
            displayCrlf(": Done!");
        }
    }

    private static void reindexVertex(String indexName, IndexSerializer indexSerializer, Set<String> entityGUIDs) throws Exception {
        Map<String, Map<String, List<IndexEntry>>> documentsPerStore = new java.util.HashMap<>();
        ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
        StandardJanusGraphTx tx = mgmt.getWrappedTx();
        BackendTransaction mutator = tx.getTxHandle();
        JanusGraphIndex index = mgmt.getGraphIndex(indexName);
        MixedIndexType indexType = (MixedIndexType) mgmt.getSchemaVertex(index).asIndexType();

        for (String entityGuid : entityGUIDs) {
            for (int attemptCount = 1; attemptCount <= MAX_TRIES_ON_FAILURE; attemptCount++) {
                AtlasVertex vertex = AtlasGraphUtilsV2.findByGuid(entityGuid);
                try {
                    indexSerializer.reindexElement(vertex.getWrappedElement(), indexType, documentsPerStore);
                    break;
                } catch (Exception e) {
                    displayCrlf("Exception: " + e.getMessage());
                    displayCrlf("Pausing before retry..");
                    Thread.sleep(2000 * attemptCount);
                }
            }
        }
        mutator.getIndexTransaction(indexType.getBackingIndexName()).restore(documentsPerStore);
    }

    private static Set<String> getEntityAndReferenceGuids(String guid) throws Exception {
        Set<String> set = new HashSet<>();
        set.add(guid);
        AtlasEntityWithExtInfo entity = atlasClientV2.getEntityByGuid(guid);
        Map<String, AtlasEntity> map = entity.getReferredEntities();
        if (map == null || map.isEmpty()) {
            return set;
        }
        set.addAll(map.keySet());
        return set;
    }

    private static void display(String... formatMessage) {
        displayFn(System.out::print, formatMessage);
    }

    private static void displayCrlf(String... formatMessage) {
        displayFn(System.out::println, formatMessage);
    }

    private static void displayFn(Consumer<String> fn, String... formatMessage) {
        if (formatMessage.length == 1) {
            fn.accept(formatMessage[0]);
        } else {
            fn.accept(String.format(formatMessage[0], formatMessage[1]));
        }
    }

    private static String[] getAtlasRESTUrl() {
        Configuration atlasConf = null;
        try {
            atlasConf = ApplicationProperties.get();
            return atlasConf.getStringArray(APPLICATION_PROPERTY_ATLAS_ENDPOINT);
        } catch (AtlasException e) {
            return new String[]{DEFAULT_ATLAS_URL};
        }
    }

    private static Configuration getAtlasConfiguration() {
        Configuration atlasConf = null;
        try {
            atlasConf = ApplicationProperties.get();
            return atlasConf;
        } catch (AtlasException e) {
            return null;
        }
    }

    private static AtlasClientV2 getAtlasClientV2(String[] atlasEndpoint, String[] uidPwdFromCommandLine) throws AtlasException {
        AtlasClientV2 atlasClientV2;
        if (!AuthenticationUtil.isKerberosAuthenticationEnabled()) {
            String[] uidPwd = (uidPwdFromCommandLine[0] == null || uidPwdFromCommandLine[1] == null)
                    ? AuthenticationUtil.getBasicAuthenticationInput()
                    : uidPwdFromCommandLine;

            atlasClientV2 = new AtlasClientV2(atlasEndpoint, uidPwd);
        } else {
            atlasClientV2 = new AtlasClientV2(atlasEndpoint);
        }
        return atlasClientV2;
    }

    /*
        This function reindexes the index with correct mapping for STRING type index and with custom normalizer
     */
    private static void fixForStringIndexAndCustomNormalizer() throws Exception {
        //Get ES Client
        LOG.info("Initliazing elastic search for custom normalizers");
        RestHighLevelClient esClient = AtlasElasticsearchDatabase.getClient();

        //Get vertext index
        GetIndexRequest getIndexRequest = new GetIndexRequest(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
        try {
            GetIndexResponse getIndexResponse = esClient.indices().get(getIndexRequest, RequestOptions.DEFAULT);

            //Create new index with same settings
            String tempIndexName = Constants.INDEX_PREFIX + Constants.VERTEX_INDEX + "_temp";
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(tempIndexName);
            Settings indexSettings = getIndexResponse.getSettings().get(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
            Map<String, Object> settingsMap = new Gson().fromJson(
                    Strings.toString(indexSettings), new TypeToken<HashMap<String, Object>>() {
                    }.getType()
            );

            Map<String, Object> indexSetting = (Map<String, Object>) settingsMap.get("index");
            if (indexSetting != null) {
                if (indexSetting.get("provided_name") != null) {
                    indexSetting.remove("provided_name");
                }
                if (indexSetting.get("creation_date") != null) {
                    indexSetting.remove("creation_date");
                }
                if (indexSetting.get("uuid") != null) {
                    indexSetting.remove("uuid");
                }
                if (indexSetting.get("version") != null) {
                    indexSetting.remove("version");
                }
                settingsMap.put("index", indexSetting);
            }

            createIndexRequest.settings(settingsMap);

            Map<String, MappingMetaData> mapping = getIndexResponse.getMappings();
            MappingMetaData mappingMetaData = mapping.get(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
            //Set mapping as keyword
            Map<String, Object> mappingSource = mappingMetaData.getSourceAsMap();

            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.map(mappingSource);
            Map<String, Object> oldMap = new Gson().fromJson(
                    Strings.toString(builder), new TypeToken<HashMap<String, Object>>() {
                    }.getType()
            );


            //Get all entity defs and fix mapping for type string with
            SearchFilter searchFilter = new SearchFilter();
            searchFilter.setParam("type", "ENTITY");

            AtlasTypeRegistry typeRegistry = new AtlasTypeRegistry();
            Set<TypeDefChangeListener> typeDefChangeListeners = Collections.EMPTY_SET;
            AtlasGraph atlasGraph = AtlasGraphProvider.getGraphInstance();
            AtlasTypeDefStore typeDefStore = new AtlasTypeDefGraphStoreV2(typeRegistry, typeDefChangeListeners, atlasGraph);
            typeDefStore.init();

            GraphBackedSearchIndexer indexer = new GraphBackedSearchIndexer(typeRegistry);
            ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
            AtlasJanusGraph atlasJanusGraph = new AtlasJanusGraph();
            AtlasJanusGraphManagement atlasJanusGraphManagement = new AtlasJanusGraphManagement(atlasJanusGraph, mgmt);

            AtlasTypesDef typeDefs = typeDefStore.searchTypesDef(searchFilter);
            Iterator it2 = typeDefs.getEntityDefs().iterator();
//            HashMap<String,Object> newMapping = new HashMap<String,Object>();
            while (it2.hasNext()) {
                AtlasEntityDef entityDef = (AtlasEntityDef) it2.next();
                Iterator it3 = entityDef.getAttributeDefs().iterator();
                while (it3.hasNext()) {
                    AtlasStructDef.AtlasAttributeDef attributeDef = (AtlasStructDef.AtlasAttributeDef) it3.next();
                    if (attributeDef.getIndexType() != null && attributeDef.getIndexType().equals(AtlasStructDef.AtlasAttributeDef.IndexType.STRING)) {

                        String qualifiedName = AtlasStructType.AtlasAttribute.getQualifiedAttributeName(entityDef, attributeDef.getName());
                        String propertyName = AtlasStructType.AtlasAttribute.generateVertexPropertyName(entityDef, attributeDef, qualifiedName);
                        //Check if properties exists
                        XContentBuilder newBuilder = XContentFactory.jsonBuilder();
                        newBuilder.startObject();
                        newBuilder.startObject("properties");
                        String[] splits = propertyName.split("\\.");
                        for (int i = 0; i < splits.length; i++) {
                            newBuilder.startObject(splits[i]);
                            if (i != splits.length - 1) {
                                newBuilder.startObject("properties");
                            }
                        }
                        newBuilder.field("type", "keyword");
                        if (attributeDef.getNormalizer() != null) {
                            newBuilder.field("normalizer", attributeDef.getNormalizer());
                        }
                        if (attributeDef.getSetupEnhancedSearch()) {
                            String jsonString = "{\r\n  \"exact\": {\r\n    \"type\": \"text\",\r\n    \"analyzer\": \"english_exact_cleaned\"\r\n  },\r\n  \"text\": {\r\n    \"type\": \"text\",\r\n    \"analyzer\": \"ignore_sepcial_characters\"\r\n  }\r\n}";
                            HashMap <String,HashMap<String,String>> fieldsParam = new Gson().fromJson(jsonString, new TypeToken<HashMap<String, HashMap<String,String>>>(){}.getType());
                            newBuilder.field("fields",fieldsParam);
                        }
                        for (int i = 0; i < splits.length; i++) {
                            newBuilder.endObject();
                            if (i != splits.length - 1) {
                                newBuilder.endObject();
                            }
                        }
                        newBuilder.endObject();
                        newBuilder.endObject();

                        //Convert to  map
                        Map<String, Object> newMap = new Gson().fromJson(
                                Strings.toString(newBuilder), new TypeToken<HashMap<String, Object>>() {
                                }.getType()
                        );
                        oldMap = deepMerge(oldMap, newMap);
                    }
                }
            }


            //Set mapping
            createIndexRequest.mapping(oldMap);

            AcknowledgedResponse createIndexResponse = esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);

            //Reindex jansugraph into temp index

            ReindexRequest reindexRequest = new ReindexRequest();
            reindexRequest.setSourceIndices(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
            reindexRequest.setDestIndex(tempIndexName);

            BulkByScrollResponse bulkResponse = esClient.reindex(reindexRequest, RequestOptions.DEFAULT);

            //Delete index
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
            AcknowledgedResponse deleteIndexResponse =
                    esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);

            //Reindex again into janusgraph index
            CreateIndexRequest createIndexRequest1 = new CreateIndexRequest(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
            createIndexRequest1.settings(settingsMap);
            createIndexRequest1.mapping(oldMap);
            AcknowledgedResponse createIndexResponse1 = esClient.indices().create(createIndexRequest1, RequestOptions.DEFAULT);


            ReindexRequest reindexRequest1 = new ReindexRequest();
            reindexRequest1.setSourceIndices(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX + "_temp");
            reindexRequest1.setDestIndex(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);

            BulkByScrollResponse bulkResponse1 = esClient.reindex(reindexRequest1, RequestOptions.DEFAULT);

            //Delete temp index
            DeleteIndexRequest deleteIndexRequest1 = new DeleteIndexRequest(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX + "_temp");
            AcknowledgedResponse deleteIndexResponse1 =
                    esClient.indices().delete(deleteIndexRequest1, RequestOptions.DEFAULT);

        } catch (Exception e) {
            throw e;
        }


    }

    // This is fancier than Map.putAll(Map)
    private static Map deepMerge(Map original, Map newMap) {
        for (Object key : newMap.keySet()) {
            if (newMap.get(key) instanceof Map && original.get(key) instanceof Map) {
                Map originalChild = (Map) original.get(key);
                Map newChild = (Map) newMap.get(key);
                original.put(key, deepMerge(originalChild, newChild));
            } else if (newMap.get(key) instanceof List && original.get(key) instanceof List) {
                List originalChild = (List) original.get(key);
                List newChild = (List) newMap.get(key);
                for (Object each : newChild) {
                    if (!originalChild.contains(each)) {
                        originalChild.add(each);
                    }
                }
            } else {
                original.put(key, newMap.get(key));
            }
        }
        return original;
    }

    private static void deleteIndices() throws Exception {
        //Get ES Client
        LOG.info("Initliazing elastic search for custom normalizers");
        RestHighLevelClient esClient = AtlasElasticsearchDatabase.getClient();


        ArrayList<String> indicesToDelete = new ArrayList<String>();
        indicesToDelete.add(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX + "_temp");
        indicesToDelete.add(Constants.INDEX_PREFIX + Constants.VERTEX_INDEX);
        indicesToDelete.add(Constants.INDEX_PREFIX + Constants.FULLTEXT_INDEX);
        indicesToDelete.add(Constants.INDEX_PREFIX + Constants.EDGE_INDEX);

        Iterator it = indicesToDelete.iterator();
        while (it.hasNext()) {
            String indexToDelete = (String) it.next();
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexToDelete);
            try {
                AcknowledgedResponse deleteIndexResponse = esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
                if (deleteIndexResponse.isAcknowledged()) {
                    LOG.info("Index ", indexToDelete, " deleted!");
                }
            } catch (ElasticsearchStatusException e) {
                if (e.status().getStatus() == 404) {
                    LOG.info(e.getMessage());
                } else {
                    throw e;
                }
            }
        }
    }

}
