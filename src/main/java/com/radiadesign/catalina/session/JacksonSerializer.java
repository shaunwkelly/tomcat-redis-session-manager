package com.radiadesign.catalina.session;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.*;
import java.util.Enumeration;


public class JacksonSerializer implements Serializer {
    private final Log log = LogFactory.getLog(RedisSessionManager.class);

    private ClassLoader loader;
    private ObjectMapper objectMapper;

    public JacksonSerializer() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        objectMapper.addMixInAnnotations(Object.class, JsonIdentityInfoMixin.class);
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @Override
    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public byte[] serialize(RedisSession redisSession) throws IOException {
        return objectMapper.writeValueAsBytes(redisSession);
    }

    @Override
    public RedisSession deserialize(byte[] data, RedisSessionManager redisSessionManager) throws IOException, ClassNotFoundException {
        RedisSession redisSession = objectMapper.readValue(data, 0, data.length, RedisSession.class);
        redisSession.setManager(redisSessionManager);
        redisSession.setCreationTime(redisSession.getCreationTimeInternal());

        return redisSession;
    }
}
