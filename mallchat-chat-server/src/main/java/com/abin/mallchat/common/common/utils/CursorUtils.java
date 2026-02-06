package com.abin.mallchat.common.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.abin.mallchat.common.common.domain.vo.request.CursorPageBaseReq;
import com.abin.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 游标分页工具类
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-28
 */
public class CursorUtils {
    /**
     * 通过Redis的ZSet获取游标分页数据
     *
     * @param cursorPageBaseReq
     * @param redisKey
     * @param typeConvert
     * @param <T>
     * @return CursorPageBaseResp<Pair<T, Double>>：返回一个游标分页响应对象，该对象里包含的数据列表，每一项都是由【业务实体 T】和【对应的分数 Double】组成的一对。"
     */
    public static <T> CursorPageBaseResp<Pair<T, Double>> getCursorPageByRedis(CursorPageBaseReq cursorPageBaseReq, String redisKey, Function<String, T> typeConvert) {
        //获取有序集合数据
        Set<ZSetOperations.TypedTuple<String>> typedTuples;
        if (StrUtil.isBlank(cursorPageBaseReq.getCursor())) {//第一次
            //获取当前页的数据
            typedTuples = RedisUtils.zReverseRangeWithScores(redisKey, cursorPageBaseReq.getPageSize());
        } else {
            typedTuples = RedisUtils.zReverseRangeByScoreWithScores(redisKey, Double.parseDouble(cursorPageBaseReq.getCursor()), cursorPageBaseReq.getPageSize());
        }
        //请从 redisKey 这个 ZSet 里，找出分数小于等于（zReverseRange... 通常表示降序）我传入的这个 Double 游标值的所有成员，然后给我 pageSize 个
        List<Pair<T, Double>> result = typedTuples
                .stream()
                //typeConvert.apply(...) 就是在执行调用者传入的那个转换逻辑，将 String 变成了 T 类型的对象
                .map(t -> Pair.of(typeConvert.apply(t.getValue()), t.getScore()))
                .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .collect(Collectors.toList());
        //获取当前返回列表中的最后一条数据，并取出它的 Pair::getValue（即它的 Double 分数），再转为 String，作为下一次请求用的新游标
        String cursor = Optional.ofNullable(CollectionUtil.getLast(result))
                .map(Pair::getValue)
                .map(String::valueOf)
                .orElse(null);
        Boolean isLast = result.size() != cursorPageBaseReq.getPageSize();
        return new CursorPageBaseResp<>(cursor, isLast, result);
    }

    /**
     * 通过mysql获取游标分页数据
     *
     * @param mapper       mapper
     * @param request      分页参数：页码，分页大小，是否倒序，游标值等等
     * @param initWrapper  初始化wrapper，作用允许调用方自定义查询条件（如where name = 'xxx'））
     * @param cursorColumn 游标字段：Lambda 表达式类型的参数，用于指定实体的游标字段（（如User::getId、Order::getCreateTime））
     * @param <T>          实体类型
     * @return
     */
    public static <T> CursorPageBaseResp<T> getCursorPageByMysql(IService<T> mapper, CursorPageBaseReq request, Consumer<LambdaQueryWrapper<T>> initWrapper, SFunction<T, ?> cursorColumn) {
        //MyBatis-Plus 的 Lambda 工具类方法，用于解析SFunction类型的 Lambda 表达式（如User::getId），返回该字段的数据类型（如Long.class、LocalDateTime.class）。例如：如果游标字段是id（Long 类型），则cursorType = Long.class；如果是createTime（LocalDateTime 类型），则cursorType = LocalDateTime.class。
        Class<?> cursorType = LambdaUtils.getReturnType(cursorColumn);
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();
        //执行调用方传入的自定义查询条件：
        //例如：调用方可能传入wrapper -> wrapper.eq(User::getStatus, 1).like(User::getName, "张三")，这样就会在 SQL 中添加WHERE status = 1 AND name LIKE '%张三%'的条件。
        // 这里利用了Consumer函数式接口的特性：接收一个参数（LambdaQueryWrapper）并执行操作，无返回值
        initWrapper.accept(wrapper);
        //游标条件
        if (StrUtil.isNotBlank(request.getCursor())) {
            //添加WHERE 游标字段 < 解析后的游标值的条件
            //例如：游标字段是id（Long 类型），请求的cursor是100，则添加WHERE id < 100。
            //parseCursor(request.getCursor(), cursorType)：自定义工具方法，将字符串类型的cursor转换为游标字段的实际类型（如将"100"转为Long，将"2025-12-17"转为LocalDateTime）。
            //为什么需要转换？因为请求中的cursor是字符串（便于传输），而数据库中的游标字段是具体类型（Long、时间等），必须类型匹配才能进行比较。
            wrapper.lt(cursorColumn, parseCursor(request.getCursor(), cursorType));
        }
        //添加ORDER BY 游标字段 DESC的排序条件（按游标字段降序排列）
        wrapper.orderByDesc(cursorColumn);
        //request.plusPage()：自定义方法，Page对象需要指定页码和页大小，例如：请求的pageSize是 20，则Page对象会设置size = 20，查询时会添加LIMIT 20的条件。
//        调用 MyBatis-Plus 的page方法执行分页查询，返回的Page对象包含：getRecords()：当前页的数据列表；其他属性（如总条数，但游标分页通常不关心总条数，因为不需要计算总页数）。
//        最终生成的 SQL 大致为：
//        SELECT * FROM table
//        WHERE [自定义条件] AND [游标字段 < 游标值]
//        ORDER BY [游标字段] DESC
//        LIMIT [pageSize];
        Page<T> page = mapper.page(request.plusPage(), wrapper);

        //这部分代码的作用是获取当前页最后一条记录的游标字段值，作为下一页的游标（字符串类型，便于传输）。
        //CollectionUtil.getLast(page.getRecords())：Hutool 工具类方法，获取集合的最后一个元素（当前页的最后一条记录）。如果当前页没有数据，返回null。
        //Optional.ofnullable(...): 如果最后一条记录是null，后续不执行
        // map(cursorColumn)：调用cursorColumn对应的 Lambda 表达式，提取该记录的游标字段值。例如：记录是User(id=95, name="李四")，游标字段是User::getId，则这里得到95
        String cursor = Optional.ofNullable(CollectionUtil.getLast(page.getRecords()))
                .map(cursorColumn)
                .map(CursorUtils::toCursor) //将游标字段的实际值转换为字符串类型的游标
                .orElse(null); //如果上述步骤任意一步为null，游标值为null
        //判断是否最后一页
        Boolean isLast = page.getRecords().size() != request.getPageSize();
        //自定义的分页响应对象，传入三个核心属性：cursor：下一页的游标值（为null表示没有下一页）；isLast：是否为最后一页；page.getRecords()：当前页的数据列表。
        return new CursorPageBaseResp<>(cursor, isLast, page.getRecords()); 
    }

    private static String toCursor(Object o) {
        if (o instanceof Date) {
            return String.valueOf(((Date) o).getTime());
        } else {
            return o.toString();
        }
    }

    /**
     * 将字符串形式的游标解析为对应类型的对象
     * <p>
     * 该方法根据游标字段的类型，将字符串游标转换为相应的对象：
     * - 如果游标字段是Date类型，则将字符串解析为时间戳对应的Date对象
     * - 否则直接返回原字符串
     *
     * @param cursor      字符串形式的游标值
     * @param cursorClass 游标字段的类型Class对象
     * @return 解析后的游标对象，可能是Date或其他类型的对象
     */
    private static Object parseCursor(String cursor, Class<?> cursorClass) {
        if (Date.class.isAssignableFrom(cursorClass)) {
            return new Date(Long.parseLong(cursor));
        } else {
            return cursor;
        }
    }
}
