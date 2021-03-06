package com.huhuo.integration.db.spring;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.huhuo.integration.base.IBaseExtenseDao;
import com.huhuo.integration.base.IBaseModel;
import com.huhuo.integration.config.GlobalConstant.DateFormat;
import com.huhuo.integration.db.mysql.Condition;
import com.huhuo.integration.db.mysql.Group;
import com.huhuo.integration.db.mysql.Order;
import com.huhuo.integration.db.mysql.Page;
import com.huhuo.integration.db.mysql.Where;
import com.huhuo.integration.exception.DaoException;
import com.huhuo.integration.util.BeanUtils;
import com.huhuo.integration.util.BeanUtils.GetterSetter;
import com.huhuo.integration.util.StringUtils;

/**
 * general DBDao support for multiple data source, supply general DB persist operation
 * @author wuyuxuan
 * @param <T>
 */
public abstract class JdbcTplBaseExtenseDao<T extends IBaseModel<Long>> 
	implements IBaseExtenseDao<T>{
	
protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	protected DataSource dataSource;
	
	private String separator = ", ";
	
	protected JdbcTemplate jdbcTpl;
	
	protected NamedParameterJdbcOperations namedParamJdbcOp;
	
	/**
	 * get table's in DB mapping this entity
	 * @return
	 */
	public abstract String getTableName();
	/**
	 * get class for generic T
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Class<T> getModelClazz(){
		return (Class<T>) ((ParameterizedType)(getClass().getGenericSuperclass())).getActualTypeArguments()[0];
	}
	/**
	 * Return the JDBC DataSource used by this DAO.
	 * subclass should specify a JdbcTemplate instance for all DB persist operation
	 * @return
	 */
	public abstract DataSource getDataSource();
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public final JdbcTemplate getJdbcTemplate() {
		if(jdbcTpl == null) {
			DataSource dataSource = getDataSource();
			if(dataSource == null) {
				throw new DaoException("==> spring bean 'dataSource' is required");
			}
			jdbcTpl = new JdbcTemplate(dataSource);
		}
		return jdbcTpl;
	}
	
	public final NamedParameterJdbcOperations getNamedParameterJdbcOperations(){
		if(namedParamJdbcOp==null){
			DataSource dataSource = getDataSource();
			if(dataSource == null) {
				throw new DaoException("==> spring bean 'dataSource' is required");
			}
			namedParamJdbcOp = new NamedParameterJdbcTemplate(dataSource);
		}
		return namedParamJdbcOp;
	}
	
	@Override
	public Boolean save(T t) throws DaoException {
		if(t == null)
			throw new DaoException("==> model t can't be null");
		if(update(t) < 1) {
			add(t);
			return true;
		}
		return false;
	}
	
	/**
	 * get field by clazz
	 * @param clazz
	 * @return
	 */
	protected String constructField(Class<T> clazz) {
		BeanUtils.GetterSetter[] getterSetterArray = JdbcTplUtils.getGetterSetter(getModelClazz());
		List<String> cols = new ArrayList<String>();
		for(final BeanUtils.GetterSetter gs : getterSetterArray){
			cols.add(gs.propertyName);
		}
		return StringUtils.join(cols, separator);
	}
	
	@Override
	public Integer add(T t) throws DaoException {
		// validate the parameter passed in
		if(t == null) {
			return null;
		}
		SimpleJdbcInsert insert = new SimpleJdbcInsert(getJdbcTemplate());
		insert.withTableName(getTableName()).usingGeneratedKeyColumns("id");
		BeanUtils.GetterSetter[] getterSetterArray = JdbcTplUtils.getGetterSetter(getModelClazz());
		Map<String, Object> args = new HashMap<String, Object>();
		List<String> cols = new ArrayList<String>();
		List<Object> values = new ArrayList<Object>();
		for(final BeanUtils.GetterSetter gs : getterSetterArray){
			// use auto increase strategy for primary key
			if("id".equals(gs.propertyName)) {
				continue;
			}
			cols.add(gs.propertyName);
			Object value = null;
			try{
				value = gs.getter.invoke(t);
			}catch(Exception e){
				logger.warn(null, e);
			}
			value = value == null ? gs.getter.getDefaultValue() : value;
			values.add(value);
			args.put(gs.propertyName, value);
		}
		insert.usingColumns(cols.toArray(new String[cols.size()]));
		Number id = insert.executeAndReturnKey(args);
		logger.debug("==> SQL --> {}", insert.getInsertString());
		logger.debug("==> params --> {}", prettyFormat(values));
		logger.debug("<== primary key return <-- {}", id);
		if(id instanceof Long)
			t.setId((Long) id);
		return 1;
		
	}
	/**
	 * add a record using primitive SQL
	 * @param t
	 * @return
	 */
	protected Integer insert(T t) {
		BeanUtils.GetterSetter[] getterSetterArray = JdbcTplUtils.getGetterSetter(t.getClass());
		final StringBuffer sb = new StringBuffer();
		List<Object> values = new ArrayList<Object>();
		sb.append("INSERT INTO ").append(getTableName()).append("(");
		boolean first = true;
		for(final BeanUtils.GetterSetter gs : getterSetterArray){
			// use auto increase strategy for primary key
			if("id".equals(gs.propertyName))
				continue;
			if(!first){
				sb.append(",");
			}else{
				first=false;
			}
			sb.append(gs.propertyName);
			Object value = null;
			try{
				value = gs.getter.invoke(t);
			}catch(Exception e){
				logger.warn(null, e);
			}
			values.add(value == null ? gs.getter.getDefaultValue() : value);
		}
		sb.append(") values(");
		for(int i=0;i<values.size();i++){
			sb.append("?");
			if(i<values.size()-1)
				sb.append(",");
		}
		sb.append(")");
		final Object[] objects = values.toArray(new Object[values.size()]);
		logger.debug("==> SQL --> {}", sb.toString());
		logger.debug("==> params --> {}", prettyFormat(values));
		int update = getJdbcTemplate().update(sb.toString(), objects);
		logger.debug("<== row affected <-- {}", update);
		return update;
		
	}
	
	@Override
	public int[] addBatch(List<T> list) {
		logger.debug("==> addBatch begin with list --> {}", prettyFormat(list));
		SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(getDataSource());
		jdbcInsert.withTableName(getTableName()).usingGeneratedKeyColumns("id");
		BeanPropertySqlParameterSource[] batch = new BeanPropertySqlParameterSource[list.size()];
		for(int i=0; i<list.size(); i++) {
			batch[i] = new BeanPropertySqlParameterSource(list.get(i));
		}
		int[] executeBatch = jdbcInsert.executeBatch(batch);
		logger.debug("<== addBatch end with affected row <-- {}", executeBatch);
		return executeBatch;
	}

	@Override
	public Integer update(T t) throws DaoException {
		// validate the parameter passed in
		if (t == null) {
			return null;
		}
		BeanUtils.GetterSetter[] getterSetterArray = JdbcTplUtils.getGetterSetter(t.getClass());
		final StringBuffer sb = new StringBuffer();
		List<Object> values = new ArrayList<Object>();
		sb.append("UPDATE ").append(getTableName()).append(" SET ");
		boolean first = true;
		for(final BeanUtils.GetterSetter gs : getterSetterArray){
			if("id".equals(gs.propertyName)) {
				continue;
			}
			if(!first){
				sb.append(",");
			}else{
				first=false;
			}
			sb.append(gs.propertyName+"=?");
			Object value = null;
			try{
				value = gs.getter.invoke(t);
			}catch(Exception e){
				logger.warn(null,e);
			}
			values.add(value==null?gs.getter.getDefaultValue():value);
		}
		sb.append(" WHERE id=?");
		values.add(t.getId());
		final Object[] objects = values.toArray(new Object[values.size()]);
		logger.debug("==> SQL --> {}", sb.toString());
//		logger.debug("==> params --> {}", StringUtils.join(objects, separator));
		logger.debug("==> params --> {}", prettyFormat(values));
		int update = getJdbcTemplate().update(sb.toString(), objects);
		logger.debug("<== row affected <-- {}", update);
		return update;
	}

	@Override
	public Integer delete(T t) throws DaoException {
		if(t == null) {
			return null;
		}
		String sql = String.format("UPDATE %s SET status=0 WHERE id=?", getTableName());
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", t.getId());
		int update = getJdbcTemplate().update(sql, t.getId());
		logger.debug("<== row affected <-- {}", update);
		return update;
	}
	
	@Override
	public <PK> Integer deleteBatch(List<PK> ids) throws DaoException {
		if(ids == null || ids.isEmpty()) {
			return null;
		}
		List<String> placeHolders = new ArrayList<String>();
		for(int i=0; i<ids.size(); i++) {
			placeHolders.add("?");
		}
		String sql = String.format("UPDATE %s SET status=0 WHERE id IN(%s)", getTableName(), 
				StringUtils.join(placeHolders, separator));
		return update(sql, ids.toArray());
	}
	
	@Override
	public Integer deletePhysical(T t) throws DaoException {
		if(t == null) {
			return null;
		}
		return deleteById(t.getId());
	}
	
	@Override
	public <PK> Integer deletePhysicalBatch(List<PK> ids) throws DaoException {
		if(ids == null || ids.isEmpty()) {
			return null;
		}
		List<String> placeHolders = new ArrayList<String>();
		for(int i=0; i<ids.size(); i++) {
			placeHolders.add("?");
		}
		String sql = String.format("DELETE FROM %s WHERE id IN(%s)", getTableName(), 
				StringUtils.join(placeHolders, separator));
		return update(sql, ids.toArray());
	}
	
	@Override
	public <PK> Integer deleteById(PK id) throws DaoException {
		String sql = String.format("DELETE FROM %s WHERE id=?", getTableName());
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", id);
		Integer update = getJdbcTemplate().update(sql, id);
		logger.debug("<== row affected <-- {}", update);
		return update;
	}
	
	@Override
	public Long count() {
		String sql = String.format("SELECT COUNT(*) FROM %s", getTableName());
		logger.debug("==> SQL --> {}", sql);
		long count = getJdbcTemplate().queryForLong(sql);
		logger.debug("<== result count <-- {}", count);
		return count;
	}
	@Override
	public List<Map<String, Object>> queryForMapList(String sql, Object... args)
			throws DaoException {
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", prettyFormat(args));
		List<Map<String, Object>> rs = getJdbcTemplate().queryForList(sql, args);
		logger.debug("<== result set <-- {}", prettyFormat(rs));
		return rs;
	}
	@Override
	public Map<String, Object> queryForMap(String sql, Object... args) throws DaoException {
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", prettyFormat(args));
		Map<String, Object> rs = getJdbcTemplate().queryForMap(sql, args);
		logger.debug("<== result set <-- {}", prettyFormat(rs));
		return rs;
	}
	@Override
	public <E> List<E> queryForList(String sql, Class<E> clazz, Object... args)
			throws DaoException {
		List<E> rs=null;
		try {
			logger.debug("==> SQL --> {}", sql);
			logger.debug("==> params --> {}", prettyFormat(args));
			rs = getJdbcTemplate().query(sql, args, new BeanPropertyRowMapper<E>(clazz));
			logger.debug("<== result set <-- {}", prettyFormat(rs));
		} catch(DaoException de){
			throw de;
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
		return rs;
	}
	
	@Override
	public <E> E queryForObject(String sql, Class<E> clazz, Object... args)
			throws DaoException {
		try {
			logger.debug("==> SQL --> {}", sql);
			logger.debug("==> params --> {}", prettyFormat(args));
			E singleResult = getJdbcTemplate().queryForObject(sql, BeanPropertyRowMapper.newInstance(clazz), args);
			logger.debug("<== result set <-- {}", prettyFormat(singleResult));
			return singleResult;
		} catch (EmptyResultDataAccessException e) {
			logger.warn("<== error cause by <-- {}", e.getMessage());
			return null;
		}
	}
	
	@Override
	public <E> E queryForSingleColVal(String sql, Class<E> requiredType,
			Object... args) throws DaoException {
		// TODO Auto-generated method stub
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", prettyFormat(args));
		E singleResult = getJdbcTemplate().queryForObject(sql, args, requiredType);
		logger.debug("<== result set <-- {}", prettyFormat(singleResult));
		return singleResult;
	}
	@Override
	public List<T> findList(String sql, Object... args) throws DaoException {
		return queryForList(sql, getModelClazz(), args);
	}
	@Override
	public <E> List<E> queryForList(String sql, Class<E> clazz, Map<String, ?> paramMap){
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", paramMap);
		
		List<E> rs = getNamedParameterJdbcOperations().query(sql, paramMap, new BeanPropertyRowMapper<E>(clazz));
		
		logger.debug("==> result set --> {}", rs==null? rs: prettyFormat(rs));
		
		return rs;
	}
	@Override
	public List<T> findList(String sql, Map<String, ?> paramMap) throws DaoException{
		return queryForList(sql, getModelClazz(), paramMap);
	}
	
	@Override
	public T findObject(String sql, Object... args) throws DaoException {
		return queryForObject(sql, getModelClazz(), args);
	}
	
	@Override
	public <PK> T find(Class<T> clazz, PK id)
			throws DaoException {
		String sql = String.format("SELECT * FROM %s WHERE id=?", getTableName());
		return queryForObject(sql, clazz, id);
	}
	
	@Override
	public <PK> T find(PK id) throws DaoException {
		return find(getModelClazz(), id);
	}
	
	@Override
	public <PK> List<T> findByIds(List<PK> idList){
		String sql = String.format("SELECT * FROM %s WHERE id IN(:ids)", getTableName());
		return findList(sql, Collections.singletonMap("ids", idList));
		
	}
	
	@Override
	public List<T> findModels(Class<T> clazz,
			Long start, Long limit) throws DaoException {
		String sql;
		if(start!=null && limit!=null) {
			sql = String.format("SELECT * FROM %s ORDER BY id DESC LIMIT %s, %s", getTableName(), start, limit);
		} else {
			sql = String.format("SELECT * FROM %s ORDER BY id DESC", getTableName());
		}
		return queryForList(sql, clazz, new Object[] {});
	}
	
	@Override
	public List<T> findModels(Long start, Long limit) throws DaoException {
		return findModels(getModelClazz(), start, limit);
	}
	
	@Override
	public List<T> findByCondition(Condition<T> condition) {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT * FROM ").append(getTableName());
			List<Object> values = constructCondition(condition, sb);
			List<T> list = findList(sb.toString(), values.toArray());
			return list;
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
	/**
	 * construct condition by condition
	 * @param condition
	 * @param sb
	 * @return
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	private List<Object> constructCondition(Condition<T> condition, StringBuilder sb)
			throws IllegalAccessException, InvocationTargetException {
		// validation
		if(condition == null) {
			return new ArrayList<Object>();
		}
		// append where clause
		List<Object> values = new ArrayList<Object>();
		T t = condition.getT();
		if (t != null) {
			boolean first = true;
			GetterSetter[] getterSetterArray = JdbcTplUtils.getGetterSetter(t.getClass());
			for(final GetterSetter gs : getterSetterArray){
				Object fieldValue = gs.getter.invoke(t);
				if(fieldValue instanceof String
						|| fieldValue instanceof Integer
						|| fieldValue instanceof Long
						|| fieldValue instanceof Float
						|| fieldValue instanceof Double
						|| fieldValue instanceof Date) {
					if(first && fieldValue!=null) {
						sb.append(" WHERE ").append(gs.propertyName);
						if(fieldValue instanceof String) {
							sb.append(" LIKE ? ");
							values.add("%" + fieldValue + "%");
						} else {
							sb.append("=? ");
							values.add(fieldValue);
						}
						first = false;
					} else if(fieldValue!=null) {
						sb.append("AND ").append(gs.propertyName);
						if(fieldValue instanceof String) {
							sb.append(" LIKE ? ");
							values.add("%" + fieldValue + "%");
						} else {
							sb.append("=? ");
							values.add(fieldValue);
						}
					}
				}
			}
		}
		// additional where clause
		List<Where> whereList = condition.getWhereList();
		if(whereList!=null && !whereList.isEmpty()) {
			boolean first = true;
			for(Where where : whereList) {
				if(first && !StringUtils.contains(sb.toString(), "WHERE")) {
					sb.append(" WHERE (").append(where.getSql()).append(")");
					first = false;
				} else {
					sb.append(" ").append(where.getJoin()).append(" (").append(where.getSql()).append(")");
				}
				values.addAll(where.getParams());
			}
		}
		// group by clause
		List<Group> groupList = condition.getGroupList();
		if(groupList!=null && groupList.size()>0) {
			boolean first = true;
			for(Group group : groupList) {
				if(first) {
					sb.append(" GROUP BY ");
					first = false;
				}
				sb.append(group.getField()).append(" ").append(group.getDir()).append(", ");
			}
			sb.deleteCharAt(sb.lastIndexOf(","));
		}
		// order clause
		List<Order> orderList = condition.getOrderList();
		if(orderList!=null && orderList.size()>0) {
			boolean first = true;
			for(Order order : orderList) {
				if(first) {
					sb.append(" ORDER BY ");
					first = false;
				}
				sb.append(order.getField()).append(" ").append(order.getDir()).append(", ");
			}
			sb.deleteCharAt(sb.lastIndexOf(","));
		}
		// limit clause
		Page<T> page = condition.getPage();
		if(page!=null && page.getStart()!=null && page.getLimit()!=null) {
			sb.append(" LIMIT ?, ?");
			values.add(page.getStart());
			values.add(page.getLimit());
		}
		sb = new StringBuilder(StringUtils.trim(sb.toString()));	// trim string
		return values;
	}
	
	@Override
	public Long countByCondition(Condition<T> condition) {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT COUNT(*) FROM ").append(getTableName());
			condition.setGroupList(new ArrayList<Group>());
			condition.setOrderList(new ArrayList<Order>());
			condition.setPage(null);
			List<Object> values = constructCondition(condition, sb);
			logger.debug("==> SQL --> {}", sb.toString());
			logger.debug("==> params --> {}", StringUtils.join(values, separator));
			long ret = getJdbcTemplate().queryForLong(sb.toString(), values.toArray());
			logger.debug("<== result set <-- {}", ret);
			return ret;
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
	@Override
	public int update(String sql, Object... args) throws DataAccessException {
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", prettyFormat(args));
		int update = getJdbcTemplate().update(sql, args);
		logger.debug("<== rows affected <-- {}", update);
		return update;
	}
	@Override
	public int[] batchUpdate(String[] sql) throws DaoException {
		logger.debug("==> SQL --> {}", prettyFormat(sql));
		logger.debug("==> params --> {}", "empty");
		int[] batchUpdate = getJdbcTemplate().batchUpdate(sql);
		logger.debug("<== batchUpdate resultset <-- {}", batchUpdate);
		return batchUpdate;
	}
	@Override
	public void execute(String sql) throws DaoException {
		logger.debug("==> SQL --> {}", sql);
		logger.debug("==> params --> {}", "empty");
		getJdbcTemplate().execute(sql);
		logger.debug("<== execute success!");
	}
	/**
	 * format obj to JSON format
	 * @param obj
	 * @return
	 */
	private String prettyFormat(Object obj) {
		return JSON.toJSONStringWithDateFormat(obj, DateFormat.LONG_FORMAT, SerializerFeature.PrettyFormat);
	}
	
}
