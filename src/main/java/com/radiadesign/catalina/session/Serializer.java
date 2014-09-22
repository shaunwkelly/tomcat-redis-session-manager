package com.radiadesign.catalina.session;

import javax.servlet.http.HttpSession;
import java.io.IOException;

public interface Serializer {
    void setClassLoader(ClassLoader loader);

    byte[] serialize(RedisSession redisSession) throws IOException;

    RedisSession deserialize(byte[] data, RedisSessionManager redisSessionManager) throws IOException, ClassNotFoundException;
}
