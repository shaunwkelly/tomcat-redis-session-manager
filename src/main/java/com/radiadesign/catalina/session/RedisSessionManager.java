package com.radiadesign.catalina.session;

import org.apache.catalina.*;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.session.ManagerBase;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.*;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionManager extends ManagerBase implements Lifecycle {
    private final Log log = LogFactory.getLog(RedisSessionManager.class);

    protected byte[] NULL_SESSION = "null".getBytes();

    protected String host = "localhost";
    protected int port = 6379;
    protected int database = 0;
    protected int retryWaitTime = 500;
    protected String password = null;
    protected int timeout = Protocol.DEFAULT_TIMEOUT;
    protected JedisPool connectionPool;
    protected JedisPoolConfig connectionPoolConfig = new JedisPoolConfig();

    protected ThreadLocal<RedisSession> currentSession = new ThreadLocal<RedisSession>();
    protected ThreadLocal<String> currentSessionId = new ThreadLocal<String>();
    protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<Boolean>();
    protected Serializer serializer;

    protected static String name = "RedisSessionManager";

    protected String serializationStrategyClass = "com.radiadesign.catalina.session.JavaSerializer";

    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSerializationStrategyClass() {
        return serializationStrategyClass;
    }

    public void setSerializationStrategyClass(String serializationStrategyClass) {
        this.serializationStrategyClass = serializationStrategyClass;
    }

    public int getMaxIdle() {
        return this.connectionPoolConfig.getMaxIdle();
    }

    public void setMaxIdle(int maxIdle) {
        this.connectionPoolConfig.setMaxIdle(maxIdle);
    }

    public int getMinIdle() {
        return this.connectionPoolConfig.getMinIdle();
    }

    public void setMinIdle(int minIdle) {
        this.connectionPoolConfig.setMinIdle(minIdle);
    }

    public int getRetryWaitTime() {
        return retryWaitTime;
    }

    public void setRetryWaitTime(int retryWaitTime) {
        this.retryWaitTime = retryWaitTime;
    }

    public RedisSessionManager() {
        super();
        Security.setProperty("networkaddress.cache.ttl", "60");
    }

    @Override
    public int getRejectedSessions() {
        // Essentially do nothing.
        return 0;
    }

    public void setRejectedSessions(int i) {
        // Do nothing.
    }

    protected Jedis acquireConnection() {
        Jedis jedis = connectionPool.getResource();

        if (getDatabase() != 0) {
            jedis.select(getDatabase());
        }

        return jedis;
    }

    protected void returnConnection(Jedis jedis, Boolean error) {
        if (error) {
            connectionPool.returnBrokenResource(jedis);
        } else {
            connectionPool.returnResource(jedis);
        }
    }

    protected void returnConnection(Jedis jedis) {
        returnConnection(jedis, false);
    }

    @Override
    public void load() throws ClassNotFoundException, IOException {

    }

    @Override
    public void unload() throws IOException {

    }

    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }

    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        setState(LifecycleState.STARTING);

        try {
            initializeSerializer();
        } catch (ClassNotFoundException e) {
            log.fatal("Unable to load serializer", e);
            throw new LifecycleException(e);
        } catch (InstantiationException e) {
            log.fatal("Unable to load serializer", e);
            throw new LifecycleException(e);
        } catch (IllegalAccessException e) {
            log.fatal("Unable to load serializer", e);
            throw new LifecycleException(e);
        } catch (ReflectiveOperationException e) {
            log.fatal("Unable to load serializer", e);
            throw new LifecycleException(e);
        }

        log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");

        initializeDatabaseConnection();

        setDistributable(true);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        if (log.isDebugEnabled()) {
            log.debug("Stopping");
        }

        setState(LifecycleState.STOPPING);

        try {
            connectionPool.destroy();
        } catch (Exception e) {
            // Do nothing.
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }

    @Override
    public Session createSession(String sessionId) {
        RedisSession session = (RedisSession) createEmptySession();

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(getMaxInactiveInterval());

        String jvmRoute = getJvmRoute();

        Boolean error = true;
        Jedis jedis = null;
        String newSessionId = sessionId;

        try {
            jedis = acquireConnection();

            // Ensure generation of a unique session identifier.
            if (sessionId == null) {
                newSessionId = generateSessionId();
            }
            if (jvmRoute != null) {
                newSessionId += '.' + jvmRoute;
            }

            // 1 = key set; 0 = key already existed
            while (jedis.setnx(newSessionId.getBytes(), NULL_SESSION) == 0L) {
                // retry or not
                if (sessionId == null) {
                    newSessionId = generateSessionId();
                } else {
                    return null;
                }

                if (jvmRoute != null) {
                    newSessionId += '.' + jvmRoute;
                }
            }

      /* Even though the key is set in Redis, we are not going to flag
         the current thread as having had the session persisted since
         the session isn't actually serialized to Redis yet.
         This ensures that the save(session) at the end of the request
         will serialize the session into Redis with 'set' instead of 'setnx'. */

            error = false;

            session.setId(newSessionId);
            session.tellNew();

            currentSession.set(session);
            currentSessionId.set(newSessionId);
            currentSessionIsPersisted.set(false);
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }

        return session;
    }

    @Override
    public Session createEmptySession() {
        return new RedisSession(this);
    }

    @Override
    public void add(Session session) {
        try {
            save(session);
        } catch (IOException ex) {
            log.warn("Unable to add to session manager store: " + ex.getMessage());
            throw new RuntimeException("Unable to add to session manager store.", ex);
        }
    }

    @Override
    public Session findSession(String id) throws IOException {
        RedisSession session;

        if (id == null) {
            session = null;
            currentSessionIsPersisted.set(false);
        } else if (id.equals(currentSessionId.get())) {
            session = currentSession.get();
        } else {
            session = loadSessionFromRedis(id);

            if (session != null) {
                session.access();
                currentSessionIsPersisted.set(true);
            }
        }

        currentSession.set(session);
        currentSessionId.set(id);

        return session;
    }

    public void clear() {
        Jedis jedis = null;
        Boolean error = true;
        try {
            jedis = acquireConnection();
            jedis.flushDB();
            error = false;
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    public int getSize() throws IOException {
        Jedis jedis = null;
        Boolean error = true;
        try {
            jedis = acquireConnection();
            int size = jedis.dbSize().intValue();
            error = false;
            return size;
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    public String[] keys() throws IOException {
        Jedis jedis = null;
        Boolean error = true;
        try {
            jedis = acquireConnection();
            Set<String> keySet = jedis.keys("*");
            error = false;
            return keySet.toArray(new String[keySet.size()]);
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    public RedisSession loadSessionFromRedis(String id) throws IOException {
        RedisSession session = null;

        Jedis jedis = null;
        Boolean error = true;

        try {
            if(log.isDebugEnabled()){
                log.debug("Attempting to load session " + id + " from Redis");
            }

            jedis = acquireConnection();
            byte[] data = loadSessionFromRedisWithRetry(id, jedis);
            error = false;

            if (data == null) {
                if(log.isDebugEnabled()){
                    log.debug("Session " + id + " not found in Redis");
                }
                session = null;
            } else if (Arrays.equals(NULL_SESSION, data)) {
                throw new IllegalStateException("Race condition encountered: attempted to load session[" + id + "] which has been created but not yet serialized.");
            } else {
                if(log.isDebugEnabled()){
                    log.debug("Deserializing session " + id + " from Redis");
                }
                session = serializer.deserialize(data, this);
                session.setId(id);
                session.setNew(false);
                session.setMaxInactiveInterval(getMaxInactiveInterval() );
                //session.access();
                //session.setValid(true);
                session.resetDirtyTracking();

                if (log.isTraceEnabled()) {
                    log.debug("Session Contents [" + id + "]:");
                    Enumeration en = session.getAttributeNames();
                    while (en.hasMoreElements()) {
                        log.debug("  " + en.nextElement());
                    }
                }
            }

            return session;
        } catch (IOException e) {
            log.fatal(e.getMessage());
            throw e;
        } catch (ClassNotFoundException ex) {
            log.fatal("Unable to deserialize into session", ex);
            throw new IOException("Unable to deserialize into session", ex);
        } catch (Exception ex) {
            log.fatal("Unable to deserialize into session", ex);
            throw new RuntimeException("Unable to deserialize into session", ex);
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    private byte[] loadSessionFromRedisWithRetry(String sessionId, Jedis jedis) throws InterruptedException {
        byte[] data = jedis.get(sessionId.getBytes());
        int retryCount = 0;
        while(Arrays.equals(NULL_SESSION, data)) {
            if(retryCount++ == 8) {
                jedis.del(sessionId);
                return data;
            }
            Thread.sleep(retryWaitTime);
            data = jedis.get(sessionId.getBytes());
        }

        return data;
    }

    public void save(Session session) throws IOException {
        Jedis jedis = null;
        Boolean error = true;

        try {
            if(log.isDebugEnabled()) {
                log.debug("Saving session " + session + " into Redis");
            }

            RedisSession redisSession = (RedisSession) session;

            if (log.isTraceEnabled()) {
                log.debug("Session Contents [" + redisSession.getId() + "]:");
                Enumeration en = redisSession.getAttributeNames();
                while (en.hasMoreElements()) {
                    log.debug("  " + en.nextElement());
                }
            }

            Boolean sessionIsDirty = redisSession.isDirty();

            redisSession.resetDirtyTracking();
            byte[] binaryId = redisSession.getId().getBytes();

            jedis = acquireConnection();

            Boolean isCurrentSessionPersisted = this.currentSessionIsPersisted.get();
            if (sessionIsDirty || (isCurrentSessionPersisted == null || !isCurrentSessionPersisted)) {
                jedis.set(binaryId, serializer.serialize(redisSession));
            }

            currentSessionIsPersisted.set(true);

            if(log.isDebugEnabled()){
                log.debug("Setting expire timeout on session [" + redisSession.getId() + "] to " + getMaxInactiveInterval());
            }
            jedis.expire(binaryId, getMaxInactiveInterval() + 90);

            error = false;
        } catch (IOException e) {
            log.error(e.getMessage());

            throw e;
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    @Override
    public void remove(Session session) {
        remove(session, false);
    }

    @Override
    public void remove(Session session, boolean update) {
        Jedis jedis = null;
        Boolean error = true;

        if(log.isDebugEnabled()){
            log.debug("Removing session ID : " + session.getId());
        }

        try {
            jedis = acquireConnection();
            jedis.del(session.getId());
            error = false;
        } finally {
            if (jedis != null) {
                returnConnection(jedis, error);
            }
        }
    }

    public void afterRequest() {
        RedisSession redisSession = currentSession.get();
        if (redisSession != null) {
            currentSession.remove();
            currentSessionId.remove();
            currentSessionIsPersisted.remove();
            if(log.isDebugEnabled()) {
                log.debug("Session removed from ThreadLocal :" + redisSession.getIdInternal());
            }
        }
    }

    @Override
    public void processExpires() {
        try {
            long timeNow = System.currentTimeMillis();
            String[] sessionIds = keys();
            if(log.isDebugEnabled()) {
                    log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessionId count " + sessionIds.length);
            }
            int expireHere = 0 ;
            for(String sessionId: sessionIds) {
                if(sessionId.startsWith("ElastiCache")) {
                    continue;
                }
                RedisSession redisSession = loadSessionFromRedis(sessionId);
                if (redisSession != null && !redisSession.isValid()) {
                    expireHere++;
                }
            }
            long timeEnd = System.currentTimeMillis();
            if(log.isDebugEnabled()) {
                log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
            }
            processingTime += ( timeEnd - timeNow );
        } catch (Exception e) {
            log.error("Error processing expired sessions", e);
        }
    }

    private void initializeDatabaseConnection() throws LifecycleException {
        try {
            // TODO: Allow configuration of pool (such as size...)
            connectionPool = new JedisPool(this.connectionPoolConfig, getHost(), getPort(), getTimeout(), getPassword());
        } catch (Exception e) {
            log.error("Error Connecting to Redis", e);
            throw new LifecycleException("Error Connecting to Redis", e);
        }
    }

    private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException, ReflectiveOperationException {
        log.info("Attempting to use serializer :" + serializationStrategyClass);
        serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

        Loader loader = null;
        Object container = null;

        try {
            Method method = super.getClass().getMethod("getContext");
            container = method.invoke(this);
        } catch (NoSuchMethodException ex) {
            Method method = super.getClass().getMethod("getContainer");
            container = method.invoke(this);
        }

        if(container != null) {
            log.error("container: " + container.getClass().getName());
            Method method = container.getClass().getMethod("getLoader");
            loader = (Loader)method.invoke(container);
        }

        ClassLoader classLoader = null;
        if (loader != null) {
            classLoader = loader.getClassLoader();
        }
        serializer.setClassLoader(classLoader);
    }
}
