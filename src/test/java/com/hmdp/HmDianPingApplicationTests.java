package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;

import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopService ShopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testSaveShop() throws InterruptedException {
        ShopService.saveShop2Redis(1L,10L);
        ShopService.saveShop2Redis(2L,10L);
        ShopService.saveShop2Redis(3L,10L);
        ShopService.saveShop2Redis(4L,10L);
        ShopService.saveShop2Redis(5L,10L);
        ShopService.saveShop2Redis(6L,10L);
    }
    @Resource
    private IUserService userService;
    private final ExecutorService es = Executors.newFixedThreadPool(500);
    long number = 13373960000L;
    @Test
    void saveUser() throws IOException {
        try (FileWriter writer = new FileWriter("tokens.txt",true)) {
            for (int i = 0; i < 100; i++) {
                String phone = String.valueOf(number);
                number++;
                User user = userService.createUserWithPhone(phone);
                // 7.保存用户信息到 redis中
                // 7.1.随机生成token，作为登录令牌
                String token = UUID.randomUUID().toString(true);
                // 7.2.将User对象转为HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                // 7.3.存储
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                // 7.4.设置token有效期
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                writer.write(token+"\n");
                writer.flush(); // 确保及时写入磁盘
            }
        }
    }


    @Test
    void testIdworker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable r = () -> {
            for(int i = 0; i < 100; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            es.submit(r);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - start));
    }


    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IShopService shopService;
//    @Resource
//    private RedissonClient redissonClient2;
//    @Resource
//    private RedissonClient redissonClient3;

    @Test
    void testRedisson() throws Exception{
        //获取锁(可重入)，指定锁的名称
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        //判断获取锁成功
        if(isLock){
            try{
                System.out.println("执行业务");
            }finally{
                //释放锁
                lock.unlock();
            }

        }
    }
    @BeforeEach
    void setUp() {
        RLock lock1 = redissonClient.getLock("anyLock");
//        RLock lock2 = redissonClient2.getLock("anyLock");
//        RLock lock3 = redissonClient3.getLock("anyLock");
        //redissonClient.getMultiLock(lock1,lock2,lock3);
    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for(int i = 0; i < 1000000; i++){
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }
}

