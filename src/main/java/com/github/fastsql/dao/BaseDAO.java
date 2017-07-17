package com.github.fastsql.dao;

import com.github.fastsql.dto.DbPageResult;
import com.github.fastsql.util.FastSqlUtils;
import com.github.fastsql.util.PageSqlUtils;
import com.github.fastsql.util.TableName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;


/**
 * E is Entity Type
 *
 * @author Jiazhi
 * @since 2017/3/30
 */
public abstract class BaseDAO<E> {
    protected Class<E> entityClass;
    protected Logger logger;
    protected String className;
    protected String tableName;

    protected NamedParameterJdbcTemplate template;

    @Autowired
    public void setTemplate(NamedParameterJdbcTemplate template) {

        this.template = template;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public BaseDAO() {
        Type type = getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            this.entityClass = (Class<E>) ((ParameterizedType) type).getActualTypeArguments()[0];
        } else {
            this.entityClass = null;
        }
        logger = LoggerFactory.getLogger(entityClass);

        className = entityClass.getSimpleName();
        TableName tableName = entityClass.getAnnotation(TableName.class);
        if (tableName != null) {
            if (StringUtils.isEmpty(tableName.value())) {
                throw new RuntimeException(entityClass + "的注解@TableName的value不能为空");
            }
            this.tableName = tableName.value();
        } else {
            this.tableName = FastSqlUtils.camelToUnderline(className);
        }

    }
    /////////////////////////////////////////////////保存方法////////////////////////////////////////

    /**
     * 插入对象中非null的值到数据库
     */
    public String saveIgnoreNull(E object) {
        String id = getSaveId(object);

        StringBuilder nameBuilder = new StringBuilder("id");
        StringBuilder valueBuilder = new StringBuilder("'" + id + "'");

        List<Method> methodList = FastSqlUtils.getAllGetterWithoutId(object);

        for (Method method : methodList) {
            try {
                Object value = method.invoke(object);
                if (value != null) {
                    String str = method.getName().replace("get", "");
                    String columnName = FastSqlUtils.camelToUnderline(str);
                    String fieldName = str.substring(0, 1).toLowerCase() + str.substring(1, str.length());
                    nameBuilder.append("," + columnName);
                    valueBuilder.append(",:" + fieldName);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String sql = "INSERT INTO " + tableName +
                "(" + nameBuilder.toString() + ") " +
                " VALUES " +
                "(" + valueBuilder.toString() + ")";

        int saveNum = template.update(
                sql, new BeanPropertySqlParameterSource(object)
        );
        if (saveNum < 1) {
            throw new RuntimeException("保存失败，saveNum = " + saveNum);
        }
        return id;
    }

    /**
     * 插入对象中的值到数据库，null值在数据库中会设置为NULL
     */
    public String save(E object) {
        String id = getSaveId(object);

        StringBuilder nameBuilder = new StringBuilder("id");
        StringBuilder valueBuilder = new StringBuilder("'" + id + "'");

        List<Method> methodList = FastSqlUtils.getAllGetterWithoutId(object);
        for (Method method : methodList) {
            try {
                Object value = method.invoke(object);
                String str = method.getName().replace("get", "");
                String columnName = FastSqlUtils.camelToUnderline(str);
                String fieldName = str.substring(0, 1).toLowerCase() + str.substring(1, str.length());

                if (value != null) {
                    nameBuilder.append("," + columnName);
                    valueBuilder.append(",:" + fieldName);
                } else {
                    nameBuilder.append("," + columnName);
                    valueBuilder.append(",NULL");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String sql = "INSERT INTO " + tableName +
                "(" + nameBuilder.toString() + ")" +
                " VALUES" +
                " (" + valueBuilder.toString() + ")";
        int saveNum = template.update(
                sql, new BeanPropertySqlParameterSource(object)
        );
        if (saveNum < 1) {
            throw new RuntimeException("保存失败，saveNum = " + saveNum);
        }
        return id;
    }

    /**
     * 获取保存对象的Id
     */
    private String getSaveId(E object) {
        String id;
        try {
            Method getId = object.getClass().getMethod("getId", new Class[]{});
            id = (String) getId.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("保存失败， getId() 方法不存在或调用失败");
        }
        if (StringUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    /////////////////////////////////////////////////删除方法////////////////////////////////////////

    /**
     * 根据id删除数据
     */
    public int delete(String id) {
        //sql
        String sql = "DELETE FROM " + tableName + " WHERE id=:id";
        //参数
        Map<String, Object> map = FastSqlUtils.mapOf("id", id);
        return template.update(sql, map);
    }

    /**
     * 删除所有数据
     */
    public int deleteAll() {
        logger.warn(tableName + "#deleteAll()删除该表所有数据 ");
        //sql
        String sql = "DELETE FROM " + tableName;
        //参数
        return template.update(sql, new HashMap<String, Object>());
    }

    /**
     * 根据id列表批量删除数据
     */
    public int deleteInBatch(List<String> ids) {
        String sql = "DELETE FROM " + tableName + " WHERE id=:id";

        MapSqlParameterSource[] parameterSources = new MapSqlParameterSource[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            parameterSources[i] = new MapSqlParameterSource("id", ids.get(i));
        }

        int[] ints = template.batchUpdate(sql, parameterSources);
        int row = 0;
        for (int i : ints) {
            if (i == 1) {
                row++;
            }
        }
        return row;
    }

    /**
     * 根据id列表批量删除数据
     */
    public int deleteInBatch(String... ids) {
        return deleteInBatch(Arrays.asList(ids));
    }

    /////////////////////////////修改 /////////////////////////////////////////////

    /**
     * 全更新 null值在 数据库中设置为null
     */
    public String update(E entity) {
//        List<Object> ignoreColumnList = Arrays.asList(ignoreColumns);

        String id;
        try {
            Method getId = entity.getClass().getMethod("getId", new Class[]{});
            id = (String) getId.invoke(entity);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("保存失败， getId() 方法不存在或调用失败");
        }
        if (StringUtils.isEmpty(id)) {
            throw new RuntimeException("修改时对象id不能为空");
        }

        StringBuilder sqlBuilder = new StringBuilder();
        List<Method> methods = FastSqlUtils.getAllGetterWithoutId(entity);
        for (Method method : methods) {
            try {
                System.out.println(method);
                Object value = method.invoke(entity);
                String columnName = FastSqlUtils.getterMethodNameToColumn(method.getName());
                String fieldName = FastSqlUtils.getterMethodNameToFieldName(method.getName());
//                String fieldName = str.substring(0, 1).toLowerCase() + str.substring(1, str.length());

//                if (ignoreColumnList.contains(columnName) || ignoreColumnList.contains(fieldName)) {
//                    continue;//忽略
//                }

                if (value != null) {
                    sqlBuilder.append("," + columnName + "=:" + fieldName);
                } else {
                    sqlBuilder.append("," + columnName + "=NULL");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String setValueSql = sqlBuilder.toString().replaceFirst(",", "");
        String sql = "UPDATE " + tableName + " SET " + setValueSql + " WHERE id=:id";//set sql


        int rows = template.update(
                sql, new BeanPropertySqlParameterSource(entity)
        );
        if (rows < 1) {
            logger.warn("修改失败");
            throw new RuntimeException("修改失败");
        }
        return id;
    }


    /**
     * 仅更新非null， null值 不更新
     */
    public String updateIgnoreNull(E entity) {
        String id;
        try {
            Method getId = entity.getClass().getMethod("getId", new Class[]{});
            id = (String) getId.invoke(entity);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("保存失败， getId() 方法不存在或调用失败");
        }
        if (StringUtils.isEmpty(id)) {
            throw new RuntimeException("修改时对象id不能为空");
        }

        StringBuilder builder = new StringBuilder();
        List<Method> methods = FastSqlUtils.getAllGetterWithoutId(entity);
        for (Method method : methods) {
            try {
                Object value = method.invoke(entity);
                if (value != null) {
                    builder.append("," + getSingleEqualsStr(method));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String sql = "UPDATE " + tableName + " SET " +
                builder.toString().replaceFirst(",", "") +
                " WHERE id=:id";

//        logger.debug(sql);
//        logger.debug(object.toString());

//        return template.update(
//                sql, new BeanPropertySqlParameterSource(object)
//        );
        int rows = template.update(
                sql, new BeanPropertySqlParameterSource(entity)
        );
        if (rows < 1) {
            logger.warn("修改失败:" + sql);
        }
        return id;
    }

    /**
     * 根据Map更新
     */
    public String update(String id, Map<String, Object> updateColumnMap) {


        StringBuilder sqlBuilder = new StringBuilder();

        for (Map.Entry<String, Object> entry : updateColumnMap.entrySet()) {
            String column = FastSqlUtils.camelToUnderline(entry.getKey());
            if (entry.getValue() != null) {
                sqlBuilder.append("," + column + "=:" + entry.getKey());
            } else {
                sqlBuilder.append("," + column + "=NULL");
            }
        }
        updateColumnMap.put("id", id);
        String sql = "UPDATE " + tableName + " SET " +
                sqlBuilder.toString().replaceFirst(",", "") +
                " WHERE id=:id";


        int rows = template.update(
                sql, updateColumnMap
        );
        if (rows < 1) {
            logger.warn("修改失败:" + sql);
        }
        return id;
    }

    /**
     * 根据Sql筛选更新
     */
    public int updateWhere(String condition, Map<String, Object> conditionMap, Map<String, Object> updateColumnMap) {


        StringBuilder sqlBuilder = new StringBuilder();

        for (Map.Entry<String, Object> entry : updateColumnMap.entrySet()) {
            String column = FastSqlUtils.camelToUnderline(entry.getKey());
            if (entry.getValue() != null) {
                sqlBuilder.append("," + column + "=:" + entry.getKey());
            } else {
                sqlBuilder.append("," + column + "=NULL");
            }
        }
        updateColumnMap.putAll(conditionMap);
        String sql = "UPDATE " + tableName + " SET " +
                sqlBuilder.toString().replaceFirst(",", "") +
                " WHERE  " + condition;


        int rows = template.update(
                sql, updateColumnMap
        );
        if (rows < 1) {
            logger.warn("修改失败:" + sql);
        }
        return rows;
    }


    //////////////////////////////find one/////////////////////////////////////

    /**
     * 通过id查找
     */
    public E findOne(String id) {
        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE id=:id";
        //参数
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);

        E returnObject;
        try {
            returnObject = template.queryForObject(
                    sql, map, new BeanPropertyRowMapper<E>(entityClass)
            );
        } catch (EmptyResultDataAccessException e) {
            logger.warn(tableName + "#findOne()返回的数据为null");
            returnObject = null;
        }
        return returnObject;
    }

    /**
     * 通过where条件查找一条记录
     * 查找姓名为1年龄大于23的记录  findOneWhere("name=:1 and age>:2", "wang",23)
     *
     * @param sqlCondition name=:1 and age=:2
     * @param values       "wang",23
     */
    public E findOneWhere(String sqlCondition, Object... values) {
        if (sqlCondition == null) {
            throw new RuntimeException("sql不能为空");
        }
        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE " + sqlCondition;

//        logger.debug(sql);
//        logger.debug(Arrays.asList(values).toString());

        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            paramMap.put("" + (i + 1), values[i]);
        }


        List<E> dateList = template.query(
                sql, paramMap, new BeanPropertyRowMapper<E>(entityClass)
        );
        if (dateList.size() == 0) {
            logger.warn(tableName + "#findOneWhere()返回的数据为null");
            return null;
        } else if (dateList.size() == 1) {
            return dateList.get(0);
        } else {
            logger.error(tableName + "#findOneWhere()返回多条数据");
            throw new RuntimeException(tableName + "#findOneWhere()返回多条数据");
        }
    }
    //////////////////////////////find list/////////////////////////////////////

//    /**
//     * 将实体中不为空字段的作为条件进行查询
//     */
//    public List<E> findListByPresentFields(E object) {
//        //sql
//        String sql = getSelectAllSqlFromEntity(object);
//        logger.debug(sql);
//        logger.debug(object.toString());
//
//        List<E> dateList = template.query(
//                sql, new BeanPropertySqlParameterSource(object), new BeanPropertyRowMapper<E>(entityClass)
//        );
//        return dateList;
//    }

    public List<E> findListWhere(String sqlCondition, Object... values) {
        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE " + sqlCondition;

        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            paramMap.put("" + (i + 1), values[i]);
        }

        return template.query(sql, paramMap, new BeanPropertyRowMapper<E>(entityClass));
    }

    public List<E> findListWhere(String sqlCondition, BeanPropertySqlParameterSource parameterSource) {

        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE " + sqlCondition;

        return template.query(sql, parameterSource, new BeanPropertyRowMapper<E>(entityClass));
    }

    public List<E> findListWhere(
            String sqlCondition,
            Map<String, Object> parameterMap) {

        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE " + sqlCondition;

        return template.query(sql, parameterMap, new BeanPropertyRowMapper<E>(entityClass));
    }

    ////////////////////////////////////count///////////////////////////////////////////


    public int countWhere(String sqlCondition, Object... values) {
        //sql
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + sqlCondition;

        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            paramMap.put("" + (i + 1), values[i]);
        }
        return template.queryForObject(sql, paramMap, Integer.class);
    }

    ////////////////page///////////////


    public DbPageResult<E> findPageWhere(int pageNumber, int perPage, String sqlCondition, Object... values) {
        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE " + sqlCondition;

        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            paramMap.put("" + (i + 1), values[i]);
        }

        List<E> coll = template.query(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                paramMap,
                new BeanPropertyRowMapper<E>(entityClass)
        );
        Integer count = template.queryForObject(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                paramMap,
                Integer.class);

        return new DbPageResult<>(coll, count);
    }


    public DbPageResult<E> findPageWhere(int pageNumber, int perPage, String sqlCondition,
                                         BeanPropertySqlParameterSource parameterSource) {
        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE 1=1 AND " + sqlCondition;

        List<E> coll = template.query(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                parameterSource,
                new BeanPropertyRowMapper<E>(entityClass)
        );
        Integer count = template.queryForObject(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                parameterSource,
                Integer.class);

        return new DbPageResult<>(coll, count);
    }

    public DbPageResult<E> findPageWhere(int pageNumber, int perPage, String sqlCondition,
                                         Map<String, Object> parameterMap) {

        //sql
        String sql = "SELECT * FROM " + tableName + " WHERE 1=1 AND" + sqlCondition;

        List<E> coll = template.query(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                parameterMap,
                new BeanPropertyRowMapper<E>(entityClass)
        );
        Integer count = template.queryForObject(
                PageSqlUtils.getPageFindSql(sql, pageNumber, perPage),
                parameterMap,
                Integer.class);

        return new DbPageResult<>(coll, count);
    }

    ///////////////////////////////////////////////BYSQL///////////////////////////
    public Map<String, Object> findMapBySql(String sql, SqlParameterSource paramSource) {
        return template.queryForMap(sql, paramSource);
    }

    public Map<String, Object> findMapBySql(String sql, Map<String, ?> paramMap) {
        return template.queryForMap(sql, paramMap);
    }


    public List<Map<String, Object>> findMapListBySql(String sql, SqlParameterSource paramSource) {
        return template.queryForList(sql, paramSource);
    }

    public List<Map<String, Object>> findMapListBySql(String sql, Map<String, ?> paramMap) {
        return template.queryForList(sql, paramMap);
    }


    private String getSingleEqualsStr(Method method) {
        String str = method.getName().replace("get", "");
        String columnName = FastSqlUtils.camelToUnderline(str);
        String fieldName = str.substring(0, 1).toLowerCase() + str.substring(1, str.length());
        return columnName + "=:" + fieldName;
    }

    //bySql

//    private String getSelectAllSqlFromEntity(E object) {
//        String baseSql = "SELECT * FROM " + tableName + " WHERE 1=1 ";
//        return baseSql + getWhereConditionFromEntity(object);
//    }
//
//    private String getCountSqlFromEntity(E object) {
//        String baseSql = "SELECT count(1) FROM " + tableName + " WHERE 1=1 ";
//        return baseSql + getWhereConditionFromEntity(object);
//    }

//    private String getWhereConditionFromEntity(E object) {
//        StringBuilder builder = new StringBuilder();
//        StringBuilder paramBuilder = new StringBuilder("{");
//        List<Method> methodList = FastSqlUtils.getAllGetter(object);
//        for (Method method : methodList) {
//            try {
//                Object fieldValue = method.invoke(object);
//                if (fieldValue != null) {
//                    String fieldStrValue = fieldValue.toString();
//                    String s = getSingleEqualsStr(method);
//                    builder.append(" AND " + s);
//                    paramBuilder.append(method.getName().substring(3) + "=" + fieldStrValue + ",");
//                }
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }
//        String sql = builder.toString();
//        logger.debug(sql);
//        logger.debug(paramBuilder.toString() + "}");
//        return sql;
//    }

}

