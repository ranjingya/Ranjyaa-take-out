package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 批量保存套餐以及和菜品的关联关系
     *
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        setmealMapper.insert(setmeal);

        Long setmealId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));

        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    @Override
    @Transactional
    public void delete(List<Long> ids) {

        if (ids.isEmpty())
            throw new DeletionNotAllowedException(MessageConstant.DELETE_NOT_CHOOSE);
        // 判断套餐是否启售
        ids.forEach(id -> {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus().equals(StatusConstant.ENABLE)) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });

        // 删除套餐的信息
        setmealMapper.deleteByIds(ids);
        // 删除套餐对应的菜品
        setmealDishMapper.deleteBySetmealIds(ids);
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id查询套餐以及对应的菜品信息
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDishes(Long id) {

        SetmealVO setmealVO = new SetmealVO();
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> dishList = setmealDishMapper.getDishesBySetmealId(id);
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(dishList);
        return setmealVO;
    }

    /**
     * 启售停售套餐
     *
     * @param status
     */
    @Override
    public void startOrStop(Integer status, Long id) {

        // 套餐为停售状态，准备启售
        if (status.equals(StatusConstant.ENABLE)) {
            // 套餐内是否存在停售的菜品
            List<Dish> dishList = dishMapper.getDishBySetmealId(id);
            if (dishList != null && !dishList.isEmpty()) {
                dishList.forEach(dish -> {
                    if (dish.getStatus().equals(StatusConstant.DISABLE))
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                });
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 修改套餐数据
     *
     * @param setmealDTO
     */
    @Override
    public void update(SetmealDTO setmealDTO) {

        // 先修改套餐信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);

        // 删除套餐中对应的菜品信息
        List<Long> ids = new ArrayList<>();
        ids.add(setmeal.getId());
        setmealDishMapper.deleteBySetmealIds(ids);

        // 重新添加菜品信息
        Long setmealId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));

        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 条件查询
     *
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     *
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}
