package models;

import java.util.Collections;
import java.util.Map;

import javax.persistence.Cacheable;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.common.collect.Maps;

class Cache {
    // 1 hour
    private static final Expiration DEFAULT_EXPIRATION = Expiration.byDeltaSeconds(60 * 60);
    public static boolean isCachable(Mapper mapper) {
        return mapper.getType().isAnnotationPresent(Cacheable.class);
    }
    
    public static <T> T get(Key k) {
        MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
        return (T) ms.get(k);
    }

    public static <T> void put(T model, Mapper<T> mapper) {
        putAll(Collections.singleton(model), mapper);
    }
    
    public static <T> void putAll(Iterable<T> models, Mapper<T> mapper) {
        Map<Key, T> map = Maps.newHashMap();
        for (T model: models) {
            map.put(mapper.toKey(model), model);
        }
        MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
        ms.putAll(map, DEFAULT_EXPIRATION);
    }
    
    public static <T> void delete(T model, Mapper<T> mapper) {
        MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
        ms.delete(mapper.toKey(model));
    }
}