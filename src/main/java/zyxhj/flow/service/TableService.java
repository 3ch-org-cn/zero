package zyxhj.flow.service;

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import zyxhj.flow.domain.TableData;
import zyxhj.flow.domain.TableQuery;
import zyxhj.flow.domain.TableSchema;
import zyxhj.flow.domain.TableVirtual;
import zyxhj.flow.repository.TableDataRepository;
import zyxhj.flow.repository.TableQueryRepository;
import zyxhj.flow.repository.TableSchemaRepository;
import zyxhj.flow.repository.TableVirtualRepository;
import zyxhj.utils.IDUtils;
import zyxhj.utils.Singleton;
import zyxhj.utils.api.BaseRC;
import zyxhj.utils.api.Controller;
import zyxhj.utils.api.ServerException;
import zyxhj.utils.data.DataSource;

public class TableService extends Controller {

	private static Logger log = LoggerFactory.getLogger(FlowService.class);

	DruidPooledConnection conn;

	private TableSchemaRepository tableSchemaRepository;
	private TableDataRepository tableDataRepository;
	private TableQueryRepository tableQueryRepository;
	private TableVirtualRepository tableVirtualRepository;
	private ScriptEngine nashorn = new ScriptEngineManager().getEngineByName("nashorn");

	public TableService(String node) {
		super(node);
		try {
			conn = DataSource.getDruidDataSource("rdsDefault.prop").getConnection();

			tableSchemaRepository = Singleton.ins(TableSchemaRepository.class);
			tableDataRepository = Singleton.ins(TableDataRepository.class);
			tableQueryRepository = Singleton.ins(TableQueryRepository.class);
			tableVirtualRepository = Singleton.ins(TableVirtualRepository.class);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	private static List<String> getJSArgs(String src) {
		int ind = 0;
		int start = 0;
		int end = 0;
		ArrayList<String> ret = new ArrayList<>();
		while (true) {
			start = src.indexOf("{{", ind);
			if (start < ind) {
				// 没有找到新的{，结束
				break;
			} else {

				// 找到{，开始找配对的}
				end = src.indexOf("}}", start);
				if (end > start + 3) {
					// 找到结束符号
					ind = end + 2;// 记录下次位置

					ret.add(src.substring(start + 2, end));
				} else {
					// 没有找到匹配的结束符号，终止循环
					break;
				}
			}
		}
		return ret;
	}

	private Object compute(String js, JSONObject tableRowData) {
		try {

			// {{c1}} + {{c2}}
			System.out.println("oldjs>>>" + js);

			List<String> args = getJSArgs(js);

			SimpleBindings simpleBindings = new SimpleBindings();
			for (String arg : args) {
				System.out.println(arg);

				simpleBindings.put(arg, tableRowData.get(arg));
			}

			js = StringUtils.replaceEach(js, new String[] { "{{", "}}" }, new String[] { "(", ")" });

			System.out.println("newjs>>>" + js);

			return nashorn.eval(js, simpleBindings);
		} catch (ScriptException e) {
			e.printStackTrace();
		}
		return null;
	}

	// 创建表结构
	@POSTAPI(//
			path = "createTableSchema", //
			des = "创建表结构表TableSchema" //
	)
	public void createTableSchema(@P(t = "表名") String alias, //
			@P(t = "表类型") Byte type, //
			@P(t = "数据列") JSONArray columns) throws Exception {
		// TODO 暂时只支持VIRTUAL_QUERY_TABLE

		TableSchema ts = new TableSchema();
		ts.id = IDUtils.getSimpleId();
		ts.alias = alias;
		ts.type = TableSchema.TYPE.VIRTUAL_QUERY_TABLE.v();

		ts.columns = columns;

		tableSchemaRepository.insert(conn, ts);
	}

	@POSTAPI(//
			path = "updateTableSchema", //
			des = "修改表结构", //
			ret = "state --- int" //
	)
	public int updateTableSchema(@P(t = "表结构编号") Long id, //
			@P(t = "表名") String alias, //
			@P(t = "数据列") JSONArray columns) throws Exception {
		TableSchema ts = new TableSchema();
		ts.alias = alias;

		// TODO 变更类型涉及到数据迁移，目前不做
		ts.type = TableSchema.TYPE.VIRTUAL_QUERY_TABLE.v();

		ts.columns = columns;

		return tableSchemaRepository.updateByKey(conn, "id", id, ts, true);
	}

	@POSTAPI(//
			path = "getTableSchemas", //
			des = "获取所有表结构", //
			ret = "List<TableSchema>" //
	)
	public List<TableSchema> getTableSchemas(//
			Integer count, //
			Integer offset//
	) throws Exception {
		return tableSchemaRepository.getList(conn, count, offset);
	}

	// 添加表数据
	@POSTAPI(//
			path = "insertTableData", //
			des = "添加表数据" //
	)
	public void insertTableData(@P(t = "表结构编号") Long tableSchemaId, //
			@P(t = "运算表数据") JSONObject data//
	) throws Exception {

		TableData td = new TableData();
		td.tableSchemaId = tableSchemaId;
		td.id = IDUtils.getSimpleId();
		td.data = data;

		// 取出计算列，进行计算
		TableSchema ts = tableSchemaRepository.getByKey(conn, "id", tableSchemaId);
		if (ts == null || ts.columns == null || ts.columns.size() <= 0) {
			// 表结构不存在，抛异常
			throw new ServerException(BaseRC.FLOW_FORM_TABLE_SCHEMA_NOT_FOUND);
		} else {
			for (int i = 0; i < ts.columns.size(); i++) {
				JSONObject jo = ts.columns.getJSONObject(i);
				String key = jo.keySet().iterator().next();
				TableSchema.Column c = jo.getObject(key, TableSchema.Column.class);

				if (c.columnType.equals(TableSchema.Column.COLUMN_TYPE_COMPUTE)) {
					// 计算列,开始计算
					System.out.println("开始计算");
					Object ret = compute(c.computeFormula, data);
					System.out.println(JSON.toJSONString(ret));

					td.data.put(key, ret);
				}
			}

			tableDataRepository.insert(conn, td);
		}
	}

	@POSTAPI(//
			path = "updateTableData", //
			des = "修改表数据", //
			ret = "state --- int")
	public int updateTableData(@P(t = "表结构编号") Long tableSchemaId, //
			@P(t = "表数据编号") Long dataId, //
			@P(t = "表数据") JSONObject data) throws Exception {

		TableData td = tableDataRepository.getByANDKeys(conn, new String[] { "table_schema_id", "id" },
				new Object[] { tableSchemaId, dataId });
		if (td == null) {
			throw new ServerException(BaseRC.FLOW_FORM_TABLE_DATA_NOT_FOUND);
		} else {

			td.data = data;

			// 取出计算列，进行计算
			TableSchema ts = tableSchemaRepository.getByKey(conn, "id", tableSchemaId);
			if (ts == null || ts.columns == null || ts.columns.size() <= 0) {
				// 表结构不存在，抛异常
				throw new ServerException(BaseRC.FLOW_FORM_TABLE_SCHEMA_NOT_FOUND);
			} else {
				for (int i = 0; i < ts.columns.size(); i++) {
					JSONObject jo = ts.columns.getJSONObject(i);
					String key = jo.keySet().iterator().next();
					TableSchema.Column c = jo.getObject(key, TableSchema.Column.class);

					if (c.columnType.equals(TableSchema.Column.COLUMN_TYPE_COMPUTE)) {
						// 计算列,开始计算
						System.out.println("开始计算");
						Object ret = compute(c.computeFormula, data);
						System.out.println(JSON.toJSONString(ret));

						td.data.put(key, ret);
					}
				}

				return tableDataRepository.updateByANDKeys(conn, new String[] { "table_schema_id", "id" },
						new Object[] { tableSchemaId, dataId }, td, true);
			}
		}
	}

	@POSTAPI(
			path = "delTableData",//
			des = "删除表数据", //
			ret = "state -- int"//
			)
	public int delTableData(
			@P(t = "表结构编号") Long tableSchemaId,//
			@P(t = "表数据编号") Long dataId//
			) throws Exception {
		return tableDataRepository.deleteByANDKeys(conn, new String[] { "table_schema_id", "id" },
				new Object[] { tableSchemaId, dataId });
	}

	// 获取数据
	@POSTAPI(
			path = "getTableDatas",//
			des = "获取表数据", //
			ret = "List<TableData>"//
			)
	public List<TableData> getTableDatas(
			@P(t = "表结构编号")Long tableSchemaId,//
			Integer count,//
			Integer offset//
			)throws Exception {
		return tableDataRepository.getListByKey(conn, "table_schema_id", tableSchemaId, count, offset);
	}

	/**
	 * 创建表查询
	 */
	@POSTAPI(
			path = "createTableQuery",//
			des = "创建表查询" //
			)
	public void createTableQuery(
			@P(t = "表结构编号")Long tableSchemaId,//
			@P(t = "查询语句")JSONObject queryFormula//
			)
			throws Exception {
		TableQuery tq = new TableQuery();
		tq.tableSchemaId = tableSchemaId;
		tq.id = IDUtils.getSimpleId();
		tq.queryFormula = queryFormula;

		tableQueryRepository.insert(conn, tq);
	}

	// 获取查询
	@POSTAPI(
			path = "getTableQueries",//
			des = "通过表结构编号获取表查询", //
			ret = "List<TableQuery>"//
			)
	public List<TableQuery> getTableQueries(
			@P(t = "表结构编号")Long tableSchemaId,//
			Integer count,//
			Integer offset//
			) throws Exception {
		return tableQueryRepository.getListByKey(conn, "table_schema_id", tableSchemaId, count, offset);
	}

	// 删除查询
	@POSTAPI(
			path = "delTableQuery",//
			des = "删除表查询", //
			ret = "state --- int"//
			)
	public int delTableQuery(
			@P(t = "表结构编号")Long tableSchemaId,//
			@P(t = "表查询编号")Long queryId//
			) throws Exception {
		return tableQueryRepository.deleteByANDKeys(conn, new String[] { "table_schema_id", "id" },
				new Object[] { tableSchemaId, queryId });
	}

	/**
	 * 根据条件查询</br>
	 */
	@POSTAPI(
			path = "getTableDatasByQuery",//
			des = "根据条件查询表数据", //
			ret = "List<TableData>"//
			)
	public List<TableData> getTableDatasByQuery(
			@P(t = "表结构编号")Long tableSchemaId,//
			@P(t = "表查询编号")Long queryId,//
			Integer count,//
			Integer offset//
			) throws Exception {

		TableQuery tq = tableQueryRepository.getByANDKeys(conn, new String[] { "table_schema_id", "id" },
				new Object[] { tableSchemaId, queryId });
		if (tq == null || tq.queryFormula == null) {
			throw new ServerException(BaseRC.FLOW_FORM_TABLE_QUERY_NOT_FOUND);
		} else {
			return getTableDatasByFormula(conn, tableSchemaId, tq.queryFormula, count, offset);
		}
	}

	public List<TableData> getTableDatasByFormula(DruidPooledConnection conn, Long tableSchemaId,
			JSONObject queryFormula, Integer count, Integer offset) throws Exception {
		return tableDataRepository.getTableDatasByQuery(conn, tableSchemaId, queryFormula, count, offset);
	}
	
	/**
	 * 创建表格可视化样式
	 * @throws ServerException 
	 */
	@POSTAPI(
			path = "createTableVirtual",//
			des = "创建表格可视化样式TableVirtual"//
			)
	public void createTableVirtual(
			@P(t = "表结构编号tableSchemaId") Long tableSchemaId,//
			@P(t = " 可视化定义（具体前端定）") String virtual//
			) throws ServerException {
		
		TableVirtual tv = new TableVirtual();
		tv.tableSchemaId = tableSchemaId;
		tv.id = IDUtils.getSimpleId();
		tv.virtual = virtual;
		tableVirtualRepository.insert(conn, tv);
	}
	
	/**
	 * 通过表结构编号TableSchemaId查询所有表格可视化样式
	 * @throws ServerException 
	 */
	@POSTAPI(
			path = "getTableVirtualList",//
			des = "通过表结构编号（tableSchemaId）查询表格可视化样式数据",//
			ret = "List<TableVirtual>"//
			)
	public List<TableVirtual> getTableVirtualList(
			@P(t = "表结构编号") Long tableSchemaId,//
			Integer count,//
			Integer offset//
			) throws ServerException{
		
		return tableVirtualRepository.getListByKey(conn, "tableSchema_id", tableSchemaId, count, offset);
		
	}
	
	/**
	 * 编辑表可视化样式数据
	 * @throws ServerException 
	 */
	@POSTAPI(
			path = "editTableVirtual",//
			des = " 编辑表可视化样式数据(TableVirtual)",
			ret = "state -- int"
			)
	public  int editTableVirtual(
			@P(t = "表结构编号") Long tableSchemaId,
			@P(t = "表可视化样式编号") Long id,
			@P(t = "表可视化样式数据")String virtual
			) throws ServerException {
		TableVirtual tv = new TableVirtual();
		tv.virtual = virtual;
		return tableVirtualRepository.updateByANDKeys(conn, new String[] {"tableSchema_id", "id"}, new Object[] {tableSchemaId, id}, tv, true);
	}
	
	/**
	 * 删除表可视化样式数据
	 * @throws ServerException 
	 */
	@POSTAPI(
			path = "delTableVirtual",//
			des = " 通过表结构编号与表可视化样式编号删除表可视化样式数据(TableVirtual)",
			ret = "state -- int"
			)
	public int delTableVirtual(
			@P(t = "表结构编号")Long tableSchemaId,
			@P(t = "表可视化样式编号")Long id//
			) throws ServerException {
		return tableVirtualRepository.deleteByANDKeys(conn, new String[] { "tableSchema_id", "id"}, new Object[] {tableSchemaId, id});
		
	}
}
