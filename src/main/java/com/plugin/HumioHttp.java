package com.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.streams.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * This is the plugin. Your class should implement one of the existing plugin
 * interfaces. (i.e. AlarmCallback, MessageInput, MessageOutput)
 */
public class HumioHttp implements MessageOutput {

	private boolean shutdown;
	private String url;
	private static final String CK_OUTPUT_API = "output_api";
	private static final Logger LOG = LoggerFactory.getLogger(HumioHttp.class);

	@Inject
	public HumioHttp(@Assisted Stream stream, @Assisted Configuration conf) throws HumioHttpException {

		url = conf.getString(CK_OUTPUT_API);
		shutdown = false;
		LOG.info(" Humio Http Plugin has been configured with the following parameters:");
		LOG.info(CK_OUTPUT_API + " : " + url);
		
		try {
            final URL urlTest = new URL(url);
        } catch (MalformedURLException e) {
        	LOG.info("Error in the given API", e);
            throw new HumioHttpException("Error while constructing the API.", e);
        }
	}

	@Override
	public boolean isRunning() {
		return !shutdown;
	}

	@Override
	public void stop() {
		shutdown = true;

	}

	@Override
	public void write(List<Message> msgs) throws Exception {
		for (Message msg : msgs) {
			writeBuffer(msg.getFields());
		}
	}

	@Override
	public void write(Message msg) throws Exception {
		if (shutdown) {
			return;
		}

		writeBuffer(msg.getFields());
	}

	public void writeBuffer(Map<String, Object> data) throws HumioHttpException {
		OkHttpClient client = new OkHttpClient();
		Gson gson = new Gson();

		try {
			final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

			RequestBody body = RequestBody.create(JSON, gson.toJson(data));
			Request request = new Request.Builder().url(url).post(body).build();
			Response response = client.newCall(request).execute();
			response.close();
			if (response.code() != 200) {
				LOG.info("Unexpected HTTP response status " + response.code());
				throw new HumioHttpException("Unexpected HTTP response status " + response.code());
			}
		} catch (IOException e) {
			LOG.info("Error while posting the stream data to the given API", e);
            throw new HumioHttpException("Error while posting stream to HTTP.", e);
		}

	}

	public interface Factory extends MessageOutput.Factory<HumioHttp> {
		@Override
		HumioHttp create(Stream stream, Configuration configuration);

		@Override
		Config getConfig();

		@Override
		Descriptor getDescriptor();
	}

	public static class Descriptor extends MessageOutput.Descriptor {
		public Descriptor() {
			super("HumioHumio Http", false, "", "Forwards stream to HTTP.");
		}
	}

	public static class Config extends MessageOutput.Config {
		@Override
		public ConfigurationRequest getRequestedConfiguration() {
			final ConfigurationRequest configurationRequest = new ConfigurationRequest();
			configurationRequest.addField(new TextField(CK_OUTPUT_API, "API to forward the stream data.", "/",
					"HTTP address where the stream data to be sent.", ConfigurationField.Optional.NOT_OPTIONAL));

			return configurationRequest;
		}
	}

	public class HumioHttpException extends Exception {

		private static final long serialVersionUID = -5301266791901423492L;

		public HumioHttpException(String msg) {
            super(msg);
        }

        public HumioHttpException(String msg, Throwable cause) {
            super(msg, cause);
        }

    }
}