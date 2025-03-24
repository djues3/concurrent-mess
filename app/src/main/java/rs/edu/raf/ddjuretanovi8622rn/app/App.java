package rs.edu.raf.ddjuretanovi8622rn.app;

// Preview feature -- Module Import Declarations
import module java.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class App {
	private static final Logger log = LoggerFactory.getLogger(App.class);

	@SuppressWarnings("rawtypes")
	// Preview feature -- Simple Source Files and Instance Main Methods
	public static void main() {
		log.info("Hello World!");
		ObjectMapper objectMapper = ObjectMapperHolder.getTomlMapper();

		try(var is = App.class.getResourceAsStream("/config.toml")) {
			Map result = objectMapper.readValue(is, Map.class);
			log.info("{}", result);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public static class ObjectMapperHolder {

		private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

		private static final TomlMapper TOML_MAPPER = new TomlMapper();

		static {
			OBJECT_MAPPER.findAndRegisterModules();
			TOML_MAPPER.findAndRegisterModules();
		}


		public static ObjectMapper getObjectMapper() {
			return OBJECT_MAPPER;
		}

		public static TomlMapper getTomlMapper() {
			return TOML_MAPPER;
		}
	}

}
