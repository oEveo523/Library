package cool.leeson.library.controller.receive;

import cool.leeson.library.config.JwtConfig;
import cool.leeson.library.entity.receive.ReceiveItemPost;
import cool.leeson.library.exceptions.MyException;
import cool.leeson.library.service.receive.ReceiveItemService;
import cool.leeson.library.service.receive.ReceiveService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * (ReceiveOrder)表控制层
 *
 * @author Leeson0202
 * @since 2023-03-17 16:43:00
 */
@RestController
@RequestMapping("receive")
public class ReceiveController {
    @Resource
    private ReceiveService receiveService;
    @Resource
    private ReceiveItemService receiveItemService;
    @Resource
    private HttpServletRequest request;

    @Resource
    private JwtConfig jwtConfig;


    /**
     * 一般预约
     *
     * @param receives 预约数据
     * @return 实体
     */
    @PostMapping
    public Map<String, Object> receive(@RequestBody List<ReceiveItemPost> receives) throws MyException {

        String userId = jwtConfig.getUsernameFromToken(request.getHeader("token"));

        return this.receiveService.receive(receives, userId);
    }

    /**
     * 查询预约行程
     *
     * @return 实体
     */
    @GetMapping("schedule")
    public Map<String, Object> query(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) throws MyException {
        String userId = jwtConfig.getUsernameFromToken(this.request.getHeader("token"));
        return this.receiveService.schedule(userId);
    }


    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("id/{id}")
    public Map<String, Object> queryById(@PathVariable("id") String id) throws MyException {
        return this.receiveItemService.queryById(id);
    }


}

