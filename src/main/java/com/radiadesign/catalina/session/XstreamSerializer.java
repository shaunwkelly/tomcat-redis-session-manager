package com.radiadesign.catalina.session;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class XstreamSerializer implements Serializer  {
    private ClassLoader classLoader;

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public byte[] serialize(RedisSession redisSession) throws IOException {
        final Kryo kryo = getKryo();
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Output output = new Output(byteArrayOutputStream);
        //kryo.writeObject(output, redisSession.getCreationTime());
        kryo.writeObject(output, redisSession);
        output.close();

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public RedisSession deserialize(byte[] data, RedisSessionManager redisSessionManager) throws IOException, ClassNotFoundException {
        final Kryo kryo = getKryo();
        final Input input = new Input(new ByteArrayInputStream(data));
        //redisSession.setCreationTime(kryo.readObject(input, long.class));
        RedisSession redisSession = kryo.readObject(input, RedisSession.class);
        redisSession.setManager(redisSessionManager);
        redisSession.setCreationTime(redisSession.getCreationTimeInternal());

        return redisSession;
    }

    private Kryo getKryo() {
        Kryo kryo = new Kryo();
        kryo.setInstantiatorStrategy(new SerializingInstantiatorStrategy());
        kryo.setClassLoader(classLoader);
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.register(RedisSession.class, 32);
        return kryo;
    }
}
