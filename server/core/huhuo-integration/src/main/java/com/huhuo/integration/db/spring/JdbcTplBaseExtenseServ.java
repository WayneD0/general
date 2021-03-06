package com.huhuo.integration.db.spring;

import java.lang.reflect.ParameterizedType;
import java.util.Date;
import java.util.List;

import com.huhuo.integration.base.BaseServ;
import com.huhuo.integration.base.IBaseExtenseDao;
import com.huhuo.integration.base.IBaseExtenseServ;
import com.huhuo.integration.base.IBaseModel;
import com.huhuo.integration.db.mysql.Condition;
import com.huhuo.integration.db.mysql.Page;
import com.huhuo.integration.exception.DaoException;

/**
 * util service associated with DAO
 * @author wuyuxuan
 * @param <T>
 */
public abstract class JdbcTplBaseExtenseServ<T extends IBaseModel<Long>> extends BaseServ implements IBaseExtenseServ<T>{

	/**
	 * get DAO for this service
	 * @return
	 */
	public abstract IBaseExtenseDao<T> getDao();
	
	public abstract void inject(T t);
	
	public void inject(List<T> list){
		if(list!=null){
			for(T t: list){
				inject(t);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public Class<T> getModelClazz(){
		return (Class<T>) ((ParameterizedType)(getClass().getGenericSuperclass())).getActualTypeArguments()[0];
	}
	
	/**
	 * set default value for common field
	 * @param t
	 * @return
	 */
	private T setDefaultValue(T t) {
		if(t.getStatus() == null) {
			t.setStatus(1);
		}
		if(t.getCreateTime() == null) {
			t.setCreateTime(new Date());
		}
		if(t.getUpdateTime() == null) {
			t.setUpdateTime(new Date());
		}
		return t;
	}
	
	@Override
	public Integer add(T t) {
		if(t == null)
			return null;
		t = setDefaultValue(t);
		return getDao().add(t);
	}

	@Override
	public <V> T find(V id) {
		return getDao().find(getModelClazz(), id);
	}

	@Override
	public Integer update(T t) {
		if(t == null)
			return null;
		t = setDefaultValue(t);
		return getDao().update(t);
	}

	@Override
	public Long count() {
		return getDao().count();
	}

	@Override
	public Integer addBatch(List<T> list) {
		if(list==null || list.isEmpty()) {
			return null;
		}
		for(T t : list) {
			t = setDefaultValue(t);
		}
		int[] addBatch = getDao().addBatch(list);
		return addBatch.length;
	}

	@Override
	public List<T> findByCondition(Condition<T> condition) {
		return findByCondition(condition, false);
	}

	@Override
	public List<T> findByCondition(Condition<T> condition, boolean injected) {
		List<T> list = getDao().findByCondition(condition);
		if(injected) {
			inject(list);
		}
		return list;
	}

	@Override
	public Long countByCondition(Condition<T> condition) {
		return getDao().countByCondition(condition);
	}

	@Override
	public List<T> findModels(Page<T> page) {
		if(page == null) {
			return null;
		}
		return  getDao().findModels(getModelClazz(), page.getStart(), page.getLimit());
	}
	
	@Override
	public <PK> List<T> findByIds(List<PK> ids) {
		return getDao().findByIds(ids);
	}

	@Override
	public Integer delete(T t) {
		return getDao().delete(t);
	}
	
	@Override
	public Integer deletePhysical(T t) throws DaoException {
		return getDao().deletePhysical(t);
	}

	@Override
	public <PK> Integer deleteBatch(List<PK> ids) throws DaoException {
		return getDao().deleteBatch(ids);
	}

	@Override
	public Boolean save(T t) throws DaoException {
		return getDao().save(t);
	}
}
