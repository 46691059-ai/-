package cn.gov.enterprise.common.persistence;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

/**
 * 平台统一Service基础实现。
 *
 * @param <M> Mapper类型
 * @param <T> 实体类型
 */
public abstract class BaseServiceImpl<M extends BaseMapperX<T>, T>
        extends ServiceImpl<M, T> implements BaseService<T> {
}
