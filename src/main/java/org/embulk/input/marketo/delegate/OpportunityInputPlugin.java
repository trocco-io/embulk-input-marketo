package org.embulk.input.marketo.delegate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.FluentIterable;
import org.apache.commons.lang3.StringUtils;
import org.embulk.base.restclient.ServiceResponseMapper;
import org.embulk.base.restclient.jackson.JacksonServiceResponseMapper;
import org.embulk.base.restclient.record.ServiceRecord;
import org.embulk.base.restclient.record.ValueLocator;
import org.embulk.config.ConfigException;
import org.embulk.input.marketo.MarketoService;
import org.embulk.input.marketo.MarketoUtils;
import org.embulk.spi.type.Types;
import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigDefault;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpportunityInputPlugin extends MarketoBaseInputPluginDelegate<OpportunityInputPlugin.PluginTask>
{
    public interface PluginTask extends MarketoBaseInputPluginDelegate.PluginTask
    {
        @Config("opportunity_filter_type")
        @ConfigDefault("\"\"")
        String getOpportunityFilterType();

        @Config("opportunity_filter_values")
        @ConfigDefault("null")
        Optional<String> getOpportunityFilterValues();
    }

    public OpportunityInputPlugin()
    {
    }

    @Override
    public void validateInputTask(PluginTask task)
    {
        super.validateInputTask(task);
        if (StringUtils.isBlank(task.getOpportunityFilterType())) {
            throw new ConfigException("`opportunity_filter_type` cannot be empty");
        }
        if (refineFilterValues(task.getOpportunityFilterValues().orElse("")).isEmpty()) {
            throw new ConfigException("`opportunity_filter_values` cannot contain empty values only");
        }
    }

    private Set<String> refineFilterValues(String filterValues)
    {
        return Stream.of(StringUtils.split(filterValues, ",")).map(StringUtils::trimToEmpty).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
    }

    @Override
    protected Iterator<ServiceRecord> getServiceRecords(MarketoService marketoService, PluginTask task)
    {
        Set<String> refinedValues = refineFilterValues(task.getOpportunityFilterValues().get());
	Iterable<ObjectNode> responseObj = marketoService.getOpportunities(task.getOpportunityFilterType(), refinedValues);
        return FluentIterable.from(responseObj).transform(MarketoUtils.TRANSFORM_OBJECT_TO_JACKSON_SERVICE_RECORD_FUNCTION).iterator();
    }

    @Override
    public ServiceResponseMapper<? extends ValueLocator> buildServiceResponseMapper(PluginTask task)
    {
        JacksonServiceResponseMapper.Builder builder = JacksonServiceResponseMapper.builder();
        builder.add("marketoGUID", Types.STRING)
		.add("createdAt", Types.TIMESTAMP, MarketoUtils.MARKETO_DATE_TIME_FORMAT)
		.add("updatedAt", Types.TIMESTAMP, MarketoUtils.MARKETO_DATE_TIME_FORMAT)
                .add("externalOpportunityId", Types.STRING);
        return builder.build();
    }
}

