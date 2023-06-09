package cool.leeson.library.service.receive;

import com.aliyuncs.utils.StringUtils;
import cool.leeson.library.config.JwtConfig;
import cool.leeson.library.config.RedisConfig;
import cool.leeson.library.dao.LibraryDao;
import cool.leeson.library.dao.LibraryRoomDao;
import cool.leeson.library.dao.LibrarySeatDao;
import cool.leeson.library.dao.ReceiveItemDao;
import cool.leeson.library.entity.receive.ReceiveItem;
import cool.leeson.library.entity.receive.ReceiveItemPost;
import cool.leeson.library.entity.receive.ReceiveItemResponse;
import cool.leeson.library.exceptions.MyException;
import cool.leeson.library.service.library.LibraryRoomService;
import cool.leeson.library.service.library.LibrarySeatService;
import cool.leeson.library.service.library.LibraryService;
import cool.leeson.library.service.library.SchoolService;
import cool.leeson.library.util.ResMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * (ReceiveOrder)表服务实现类
 *
 * @author Leeson0202
 * @since 2023-03-17 16:43:01
 */
@Service("receiveService")
@Slf4j
@Transactional
public class ReceiveService {
    @Resource
    private ReceiveItemDao receiveItemDao;
    @Resource
    private LibraryDao libraryDao;
    @Resource
    private LibraryRoomDao libraryRoomDao;
    @Resource
    private LibrarySeatDao librarySeatDao;

    @Resource
    private SchoolService schoolService;
    @Resource
    private LibraryService libraryService;
    @Resource
    private LibraryRoomService libraryRoomService;
    @Resource
    private LibrarySeatService librarySeatService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private HttpServletRequest request;

    @Resource
    private JwtConfig jwtConfig;
    //    seatKey
    private String seatKeyFormat = "%s:%s:%s"; // seatId:day:timeIdx   dsackacbjakcaw3:16:1
    // 用户key
    private String userKeyFormat = "%s:%s:%s";  // userId:day:timeIdx  dfjank:12:0
    private String onlineKeyFormat = "%s:online";  // userId:online  dfjank:online

    /**
     * 提交预约订单
     *
     * @param receiveItemPosts 数据
     * @return 实体
     */
    public Map<String, Object> receive(List<ReceiveItemPost> receiveItemPosts, String userId) throws MyException {
        if (receiveItemPosts == null || receiveItemPosts.size() == 0)
            throw new MyException(MyException.STATUS.requestErr);
        log.info(receiveItemPosts.toString());
        // 数据 String orderId = UUID.randomUUID().toString();
        Date now = new Date();
        // 构建 receiveItems
        List<ReceiveItem> receiveItems = new ArrayList<>();
        for (ReceiveItemPost receiveItemPost : receiveItemPosts) {
            int today = receiveItemPost.getToday() ? 0 : 1;
            // 查询座位是否占用
            String seatKey = String.format(seatKeyFormat, receiveItemPost.getSeatId(), now.getDate() + today, receiveItemPost.getTimeIdx());
            String rSeatRecord = redisTemplate.opsForValue().get(seatKey);
            if (!StringUtils.isEmpty(rSeatRecord) && "true".equals(rSeatRecord)) {
                // 说明有数据
                return ResMap.err("座位已占座，请刷新");
            }
            // 查询用户在该时段是否预约
            String userKey = String.format(userKeyFormat, userId, now.getDate() + today, receiveItemPost.getTimeIdx());
            String rUserRecord = redisTemplate.opsForValue().get(userKey);
            if (!StringUtils.isEmpty(rUserRecord) && "true".equals(rUserRecord)) {
                // 说明有数据
                return ResMap.err("您已经预约了该时段");
            }
            // 构建 receiveItem

//            LocalTime local = LocalTime.of(timeIdx * 2 + 8, 0, 0, 0); 构建时间的
            ReceiveItem receiveItem = new ReceiveItem(UUID.randomUUID().toString(), userId, receiveItemPost.getLibraryId(), receiveItemPost.getRoomId(), receiveItemPost.getSeatId(), new Date(now.getYear(), now.getMonth(), now.getDate() + today), receiveItemPost.getTimeIdx(), now);
            receiveItems.add(receiveItem);
        }
        // 插入 items
        if (this.receiveItemDao.insertBatch(receiveItems) < receiveItems.size()) {
            throw new MyException(MyException.STATUS.err);
        }
        // 座位和用户信息，插入redis
        for (ReceiveItemPost receiveItemPost : receiveItemPosts) {
            int today = receiveItemPost.getToday() ? 0 : 1;
            String seatKey = String.format(seatKeyFormat, receiveItemPost.getSeatId(), now.getDate() + today, receiveItemPost.getTimeIdx());
            String userKey = String.format(userKeyFormat, userId, now.getDate() + today, receiveItemPost.getTimeIdx());
            // 剩下的时间
            long milliSecondsLeftToday = 86400000 - DateUtils.getFragmentInMilliseconds(Calendar.getInstance(), Calendar.DATE);
            milliSecondsLeftToday = receiveItemPost.getToday() ? milliSecondsLeftToday : milliSecondsLeftToday + 86400000;
            // 插入
            redisTemplate.opsForValue().set(seatKey, "true", milliSecondsLeftToday, TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(userKey, "true", milliSecondsLeftToday, TimeUnit.MILLISECONDS);
        }
        return this.schedule(userId);
    }

    /**
     * 获取用户的行程计划
     *
     * @param userId 用户Id
     * @return 实体
     * @throws MyException ms
     */
    public Map<String, Object> schedule(String userId) throws MyException {
        log.info(userId + " 正在获取行程计划");
        // 先查询所有
        List<ReceiveItem> items = this.receiveItemDao.queryByUserId(userId);
        log.info("用户的items：" + items.size());
        Date now = new Date();
        log.info("现在的时间: " + now.toLocaleString());
        // 时间Idx
        int timeIndex = 0;
        int today = 0;
        if (now.getHours() < 10) {
            timeIndex = 0;
        } else if (now.getHours() >= 22) {
            // 第二天
            timeIndex = 0;
            today = 1;
        } else {
            timeIndex = (now.getHours() - 8) / 2;
        }
        log.info("现在的timeIndex: " + timeIndex + "; today: " + today);
        // 要展现的时间 年月日
        Date date = new Date(now.getYear(), now.getMonth(), now.getDate() + today);
        List<ReceiveItemResponse> responseItems = new ArrayList<>();
        for (ReceiveItem item : items) {
            log.info("现在遍历第 " + items.indexOf(item) + "个");
            log.info("item信息： " + item.getReceiveDate().toLocaleString() + " timeIdx: " + item.getTimeIdx());

            // 比这个时间早，直接继续 日期早或index小
            if (item.getReceiveDate().before(date) || (item.getReceiveDate().equals(date) && item.getTimeIdx() < timeIndex)) {
                log.info("这个预约在这之前，直接跳过\n-----------------");
                continue;
            }
            // 判断 status
            int status = (item.getReceiveDate().equals(date) && item.getTimeIdx() == timeIndex) ? 1 : 0;
            // 判断是否在线 online
            String onlineKey = String.format(RedisConfig.FormatKey.ONLINE.toString(), userId);
            String userOnline = redisTemplate.opsForValue().get(onlineKey);
            int online = 0; // 未入座
            if (!StringUtils.isEmpty(userOnline) && !"".equals(userOnline)) {
                online = Integer.parseInt(userOnline); // 1入座 2 暂时离开
            }
            log.info(" status: " + status + "; online: " + online);
            // 构建
            ReceiveItemResponse responseItem = new ReceiveItemResponse(item, status, online);
            // date
            String ddate = item.getReceiveDate().getMonth() + 1 + "月" + item.getReceiveDate().getDate() + "日";
            log.info(" 构建的日期：" + date);
            // 获取名字
            String libraryName = this.libraryService.querySimple(item.getLibraryId()).getName();
            String roomName = this.libraryRoomService.querySimple(item.getRoomId()).getName();
            String seatName = this.librarySeatService.querySimple(item.getSeatId()).getName();
            responseItem.setDate(ddate);
            responseItem.setLibraryName(libraryName);
            responseItem.setRoomName(roomName);
            responseItem.setSeatName(seatName);
            responseItems.add(responseItem);
            log.info("-------------");
        }
        // 排序
        Collections.sort(responseItems);
        return ResMap.ok(responseItems);

    }

    /**
     * 获取用户的所有预约
     *
     * @param userId 用户Id
     * @return 实体
     * @throws MyException ms
     */
    public Map<String, Object> all(String userId) throws MyException {
        List<ReceiveItem> items = this.receiveItemDao.queryByUserId(userId);
        return ResMap.ok();
    }
}
