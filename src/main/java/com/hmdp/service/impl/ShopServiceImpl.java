package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        //防缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        Shop shop = queryWithMutex(id);
        //Shop shop = queryWithLogicalExpire(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    private boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        updateById(shop);
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺id为空");
        }
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    public Shop queryWithPassThrough(Long id){
        //防缓存穿透
        String key = CACHE_SHOP_KEY + id;
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopjson)) {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        if(shopjson != null){
            return null;
        }
        Shop shop = getById(id);
        if(shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        return shop;
    }

    public Shop queryWithMutex(Long id){
        //锁被提前释放导致多次查询
//        int maxtry = 10;
//        int nowtry = 0;
//        while(nowtry < maxtry){
//            //System.out.println("check");
//            //互斥锁
//            String key = CACHE_SHOP_KEY + id;
//            String shopjson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopjson)) {
//                Shop shop = JSONUtil.toBean(shopjson, Shop.class);
//                return shop;
//            }
//            if(shopjson != null){
//                return null;
//            }
//            Shop shop = null;
//            try {
//                boolean trylock = trylock(LOCK_SHOP_KEY + id);
//                System.out.println(trylock);
//                if(!trylock){
//                    Thread.sleep(100);
//                    nowtry++;
//                    //System.out.println(nowtry);
//                    break;
//                }
//                //System.out.println("doublecheck");
//                shopjson = stringRedisTemplate.opsForValue().get(key);
//                if (StrUtil.isNotBlank(shopjson)) {
//                    return JSONUtil.toBean(shopjson, Shop.class);
//                }
//                if (shopjson != null) {
//                    return null;
//                }
//                //System.out.println("查数据库");
//                shop = getById(id);
//                //模拟延迟
//                Thread.sleep(200);
//                if(shop == null) {
//                    stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                    return null;
//                }
//                stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            } finally {
//                System.out.println("释放锁");
//                unlock(LOCK_SHOP_KEY + id);
//                //
//            }
//            return shop;
//        }
//        return null;


        //互斥锁
        String key = CACHE_SHOP_KEY + id;
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopjson)) {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        if(shopjson != null){
            return null;
        }
        Shop shop = null;
        try {
            boolean trylock = trylock(LOCK_SHOP_KEY + id);
            if(!trylock){
                Thread.sleep(50);
                queryWithMutex(id);
            }
            shopjson = stringRedisTemplate.opsForValue().get(key);
            System.out.println("doublecheck");
            if (StrUtil.isNotBlank(shopjson)) {
                return JSONUtil.toBean(shopjson, Shop.class);
            }
            if (shopjson != null) {
                return null;
            }
            System.out.println("查数据库");
            shop = getById(id);
            //模拟延迟
            Thread.sleep(200);
            if(shop == null) {
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("释放锁");
            unlock(LOCK_SHOP_KEY + id);
        }
        return shop;



    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000), //5000m
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }

    public Shop queryWithLogicalExpire(Long id){
        //缓存预热 不需要考虑缓存穿透
        String key = CACHE_SHOP_KEY + id;
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopjson)) {
            return null;
        }
        RedisData RedisData = JSONUtil.toBean(shopjson, RedisData.class);
        LocalDateTime expiertime = RedisData.getExpireTime();
        JSONObject jsonObject = (JSONObject) RedisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject,Shop.class);
        if(expiertime.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lockkey = LOCK_SHOP_KEY + id;
        boolean isLock = trylock(lockkey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockkey);
                }
            });
        }
        return shop;
    }

}
