package com.greate.community.service;

import com.greate.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 网站数据统计（UV / DAU）
 */
@Service
public class DataService {

    @Autowired
    private RedisTemplate redisTemplate;

    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 将指定的 IP 计入当天的 UV
     * @param ip
     */
    public void recordUV(String ip) {
        String redisKey = RedisKeyUtil.getUVKey(df.format(new Date()));
        redisTemplate.opsForHyperLogLog().add(redisKey, ip);
    }

    /**
     * 统计指定日期范围内的 UV
     * @param start
     * @param end
     * @return
     */
    public long calculateUV(Date start, Date end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // 整理该日期范围内的 key
        List<String> keyList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        while (!calendar.getTime().after(end)) {
            String key = RedisKeyUtil.getUVKey(df.format(calendar.getTime()));
            keyList.add(key);
            calendar.add(Calendar.DATE, 1); // 加1天
        }

        // 合并这些天的 UV
        String redisKey = RedisKeyUtil.getUVKey(df.format(start), df.format(end));
        redisTemplate.opsForHyperLogLog().union(redisKey, keyList.toArray());

        // 返回统计结果
        return redisTemplate.opsForHyperLogLog().size(redisKey);
    }

    /**
     * 将指定的 IP 计入当天的 DAU
     * @param userId
     */
    public void recordDAU(int userId) {
        String redisKey = RedisKeyUtil.getDAUKey(df.format(new Date()));
        redisTemplate.opsForValue().setBit(redisKey, userId, true);
    }

    /**
     * 统计指定日期范围内的 DAU
     * @param start
     * @param end
     * @return
     */
    public long calculateDAU(Date start, Date end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // 整理该日期范围内的 key
        List<byte[]> keyList = new ArrayList<>();
        //Calendar 类本身并不控制日期的格式化，它主要用于处理日期和时间的计算和操作
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        //遍历每一天的日期作为key存在keyList
        while (!calendar.getTime().after(end)) {
            String key = RedisKeyUtil.getDAUKey(df.format(calendar.getTime()));
            keyList.add(key.getBytes());
            calendar.add(Calendar.DATE, 1); // 加1天
        }

        // 进行 or 运算，将范围内每一天的 key 取出来，调用 connection.bitOp 操作将每一天的结果联合起来，求合集的总数
        return (long) redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                //在给定的日期范围内，每一天的用户活跃情况可以用一个位图来表示，其中每个用户的活跃状态用位来表示，比如 1 表示活跃，0 表示不活跃。
                //通过对这些位图进行位运算，可以得到给定日期范围内所有天的活跃用户的合集。
                String redisKey = RedisKeyUtil.getDAUKey(df.format(start), df.format(end));
                redisConnection.bitOp(RedisStringCommands.BitOperation.OR,
                        redisKey.getBytes(), keyList.toArray(new byte[0][0]));
                return redisConnection.bitCount(redisKey.getBytes());
            }
        });
    }
}
