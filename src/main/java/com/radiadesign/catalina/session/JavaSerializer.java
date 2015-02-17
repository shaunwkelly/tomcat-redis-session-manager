package com.radiadesign.catalina.session;

import org.apache.catalina.util.CustomObjectInputStream;

import javax.servlet.http.HttpSession;
import java.io.*;


public class JavaSerializer implements Serializer {
    private ClassLoader loader;

    @Override
    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public byte[] serialize(RedisSession redisSession) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
        oos.writeLong(redisSession.getCreationTime());
        redisSession.writeObjectData(oos);

        oos.close();

        return bos.toByteArray();
    }

    @Override
    public RedisSession deserialize(byte[] data, RedisSessionManager redisSessionManager) throws IOException, ClassNotFoundException {
        RedisSession redisSession = (RedisSession)redisSessionManager.createEmptySession();
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
        ObjectInputStream ois = new CustomObjectInputStream(bis, loader);
        redisSession.setCreationTime(ois.readLong());
        redisSession.readObjectData(ois);
        ois.close();

        return redisSession;
    }
}
