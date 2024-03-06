package org.embulk.input.marketo.delegate;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.embulk.EmbulkTestRuntime;
import org.embulk.base.restclient.ServiceResponseMapper;
import org.embulk.base.restclient.record.RecordImporter;
import org.embulk.base.restclient.record.ValueLocator;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigLoader;
import org.embulk.config.ConfigSource;
import org.embulk.input.marketo.rest.MarketoRestClient;
import org.embulk.input.marketo.rest.RecordPagingIterable;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.Schema;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.embulk.input.marketo.MarketoUtilsTest.CONFIG_MAPPER;
import static org.embulk.input.marketo.delegate.OpportunityInputPlugin.PluginTask;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpportunityInputPluginTest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Rule
    public EmbulkTestRuntime embulkTestRuntime = new EmbulkTestRuntime();

    private OpportunityInputPlugin opportunityInputPlugin;

    private ConfigSource configSource;

    private MarketoRestClient mockMarketoRestClient;

    @Before
    public void setUp() throws Exception
    {
        opportunityInputPlugin = spy(new OpportunityInputPlugin());
        ConfigLoader configLoader = embulkTestRuntime.getInjector().getInstance(ConfigLoader.class);
        configSource = configLoader.fromYaml(this.getClass().getResourceAsStream("/config/opportunity.yaml"));
        mockMarketoRestClient = mock(MarketoRestClient.class);
        doReturn(mockMarketoRestClient).when(opportunityInputPlugin).createMarketoRestClient(any(PluginTask.class));
    }

    @Test
    public void testValidPluginTask()
    {
        PluginTask pluginTask = mapTask(configSource);
        opportunityInputPlugin.validateInputTask(pluginTask);
    }

    @Test
    public void testCustomObjectFilterTypeError()
    {
        PluginTask pluginTask = mapTask(configSource.set("opportunity_filter_type", ""));
        Assert.assertThrows(ConfigException.class, () -> opportunityInputPlugin.validateInputTask(pluginTask));
    }

    @Test
    public void testEmptyStringFilterValues()
    {
        PluginTask pluginTask = mapTask(configSource.set("opportunity_filter_values", ""));
        Assert.assertThrows(ConfigException.class, () -> opportunityInputPlugin.validateInputTask(pluginTask));
    }

    @Test
    public void testAllEmptyStringFilterValues()
    {
        PluginTask pluginTask = mapTask(configSource.set("opportunity_filter_values", ",, , "));
        Assert.assertThrows(ConfigException.class, () -> opportunityInputPlugin.validateInputTask(pluginTask));
    }

    @Test
    public void testRun() throws IOException
    {
        RecordPagingIterable<ObjectNode> mockRecordPagingIterable = mock(RecordPagingIterable.class);
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametrizedType(List.class, List.class, ObjectNode.class);
        List<ObjectNode> objectNodeList = OBJECT_MAPPER.readValue(this.getClass().getResourceAsStream("/fixtures/opportunity_response.json"), javaType);
        when(mockRecordPagingIterable.iterator()).thenReturn(objectNodeList.iterator());
        when(mockMarketoRestClient.getOpportunities(anyString(), anyString())).thenReturn(mockRecordPagingIterable);

        PluginTask task = mapTask(configSource);
        ServiceResponseMapper<? extends ValueLocator> mapper = opportunityInputPlugin.buildServiceResponseMapper(task);
        RecordImporter recordImporter = mapper.createRecordImporter();
        PageBuilder mockPageBuilder = mock(PageBuilder.class);
        opportunityInputPlugin.ingestServiceData(task, recordImporter, 1, mockPageBuilder);
        verify(mockMarketoRestClient, times(1)).getOpportunities(anyString(), anyString());

        Schema schema = mapper.getEmbulkSchema();
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPageBuilder, times(3)).setString(eq(schema.lookupColumn("externalOpportunityId")), stringArgumentCaptor.capture());
        List<String> allValues = stringArgumentCaptor.getAllValues();
        assertArrayEquals(new String[]{"externalOpportunityId_1", "externalOpportunityId_2", "externalOpportunityId_3"}, allValues.toArray());
    }

    private PluginTask mapTask(ConfigSource config)
    {
        return CONFIG_MAPPER.map(config, PluginTask.class);
    }
}

