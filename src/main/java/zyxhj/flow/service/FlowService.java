package zyxhj.flow.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import zyxhj.flow.domain.Module;
import zyxhj.flow.domain.Process;
import zyxhj.flow.domain.ProcessActivity;
import zyxhj.flow.domain.ProcessAsset;
import zyxhj.flow.domain.ProcessDefinition;
import zyxhj.flow.domain.ProcessLog;
import zyxhj.flow.repository.ModuleRepository;
import zyxhj.flow.repository.ProcessActivityRepository;
import zyxhj.flow.repository.ProcessAssetRepository;
import zyxhj.flow.repository.ProcessDefinitionRepository;
import zyxhj.flow.repository.ProcessLogRepository;
import zyxhj.flow.repository.ProcessRepository;
import zyxhj.utils.IDUtils;
import zyxhj.utils.Singleton;
import zyxhj.utils.api.Controller;
import zyxhj.utils.api.ServerException;
import zyxhj.utils.data.DataSource;

public class FlowService extends Controller {

	private static Logger log = LoggerFactory.getLogger(FlowService.class);

	private ProcessRepository processRepository;
	private ProcessDefinitionRepository processDefinitionRepository;
	private ProcessActivityRepository processActivityRepository;

	private ProcessLogRepository processLogRepository;
	private ModuleRepository moduleRepository;
	private ProcessAssetRepository processAssetRepository;

	private DruidPooledConnection conn;

	public FlowService(String node) {
		super(node);
		try {
			conn = DataSource.getDruidDataSource("rdsDefault.prop").getConnection();

			processRepository = Singleton.ins(ProcessRepository.class);
			processDefinitionRepository = Singleton.ins(ProcessDefinitionRepository.class);
			processActivityRepository = Singleton.ins(ProcessActivityRepository.class);
			moduleRepository = Singleton.ins(ModuleRepository.class);
			processLogRepository = Singleton.ins(ProcessLogRepository.class);
			processAssetRepository = Singleton.ins(ProcessAssetRepository.class);

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	/*
	 * ProcessDefinitionService
	 */
	/**
	 * 创建流程定义
	 */
	@POSTAPI(//
			path = "createProcessDefinition", //
			des = "创建ProcessDefinition流程定义", //
			ret = "Definition编号" //
	)
	public Long createProcessDefinition(@P(t = "moduleId应用或平台编号，现未使用") Long moduleId, @P(t = "流程定义标题") String title,
			@P(t = "标签列表") JSONArray tags, @P(t = "流程图泳道名称列表，泳道名称不可重复") JSONArray lanes) throws Exception {

		Long id = IDUtils.getSimpleId();

		ProcessDefinition pd = new ProcessDefinition();
		pd.moduleId = moduleId;
		pd.id = id;
		pd.tags = tags;
		pd.status = ProcessDefinition.STATUS_READY;
		pd.title = title;

		pd.lanes = lanes;

		processDefinitionRepository.insert(conn, pd);
		return pd.id;
	}

	/*
	 * 编辑表流程定义
	 */
	@POSTAPI(//
			path = "editProcessDefinition", //
			des = "编辑ProcessDefinition流程定义", //
			ret = "status-----int"//
	)
	public int editProcessDefinition(@P(t = "流程定义表编号") Long id, //
			@P(t = "流程定义状态") Byte status, //
			@P(t = "标签列表") JSONArray tags, //
			@P(t = "流程定义标题") String title, //
			@P(t = "流程图泳道名称列表，泳道名称不可重复") JSONArray lanes) throws Exception {

		ProcessDefinition renew = new ProcessDefinition();
		renew.tags = tags;
		renew.status = status;
		renew.title = title;

		renew.lanes = lanes;

		return processDefinitionRepository.updateByKey(conn, "id", id, renew, true);
	}

	/*
	 * 查询所有表流程定义
	 */
	@POSTAPI(//
			path = "getProcessDefinition", //
			des = "查询所有流程定义", //
			ret = "List<ProcessDefinition>"//
	)
	public List<ProcessDefinition> getProcessDefinition(//
			@P(t = "应用或平台编号") Long moduleId, //
			Integer count, //
			Integer offset//
	) throws Exception {
		return processDefinitionRepository.getListByKey(conn, "module_id", moduleId, count, offset);
	}

	/*
	 * ProcessActivityService
	 */
	/**
	 * 设置流程节点，没有就增加，有就设置</br>
	 * 重名会被覆盖
	 */

	@POSTAPI(//
			path = "createProcessActivity", //
			des = "创建ProcessActivity流程节点"//
	)
	public Long createProcessActivity(@P(t = "流程定义编号") Long pdId, //
			@P(t = "活动标题") String title, //
			@P(t = "所属泳道") String part, //
			@P(t = "接收者（departments部门，roles角色，users用户）") JSONArray receivers, //
			@P(t = "行为动作") JSONArray actions//
	) throws Exception {
		Long id = IDUtils.getSimpleId();
		ProcessActivity pa = new ProcessActivity();
		pa.pdId = pdId;
		pa.id = id;

		pa.title = title;
		pa.part = part;
		pa.receivers = receivers;
		pa.actions = actions;

		processActivityRepository.insert(conn, pa);

		return pa.id;
	}

	/**
	 * 删除流程节点
	 *
	 */
	@POSTAPI(//
			path = "delPDActivity", //
			des = "删除ProcessActivity节点"//
	)
	// TODO 逻辑删除，增加已删除状态，将来做清理功能，先删除附件等相关资源，再删除Activity
	public int delPDActivity(//
			@P(t = "流程定义编号") Long pdId, //
			@P(t = "流程节点编号") Long activityId//
	) throws Exception {
		return processActivityRepository.deleteByANDKeys(conn, new String[] { "pd_id", "id" },
				new Object[] { pdId, activityId });
	}

	/**
	 * 编辑流程节点
	 */
	@POSTAPI(//
			path = "editPDActivity", //
			des = "编辑ProcessActivity节点", //
			ret = "state ---- int")
	public int editPDActivity(//
			@P(t = "流程定义编号") Long pdId, //
			@P(t = "流程节点编号") Long id, //
			@P(t = "活动标题") String title, //
			@P(t = "所属泳道") String part, //
			@P(t = "接收者（departments部门，roles角色，users用户）") JSONArray receivers, //
			@P(t = "行为动作") JSONArray actions//
	) throws Exception {

		ProcessActivity renew = new ProcessActivity();
		renew.title = title;
		renew.part = part;
		renew.receivers = receivers;
		renew.actions = actions;

		return processActivityRepository.updateByANDKeys(null, new String[] { "pd_id", "id" },
				new Object[] { pdId, id }, renew, true);
	}

	/*
	 * 查询所有流程节点
	 */

	@POSTAPI(//
			path = "getPDActivityList", //
			des = "获取所有ProcessActivity节点", //
			ret = "List<ProcessActivity>")
	public List<ProcessActivity> getPDActivityList(//
			@P(t = "DefinitionId 流程定义编号") Long pdId, //
			Integer count, //
			Integer offset//
	) throws ServerException {

		return processActivityRepository.getListByKey(conn, "pd_id", pdId, count, offset);

	}
	/*
	 * ProcessService
	 */

	/*
	 * 创建Process流程实例
	 */
	@POSTAPI(//
			path = "createProcess", //
			des = "创建Process", //
			ret = "ProcessId流程实例")
	public Process createProcess(//
			@P(t = "流程定义编号") Long pdId, //
			@P(t = "流程标题") String title, //
			@P(t = "流程节点编号") Long currActivityId, //
			String remark//
	) throws ServerException {
		Long id = IDUtils.getSimpleId();
		Process pro = new Process();
		pro.pdId = pdId;
		pro.id = id;
		pro.title = title;
		pro.currActivityId = currActivityId;
		pro.timestamp = new Date();
		pro.remark = remark;

		processRepository.insert(conn, pro);

		return pro;
	}

	/*
	 * 编辑process流程实例
	 */
	// TODO 增加状态
	@POSTAPI(//
			path = "editProcess", //
			des = "创建Process", //
			ret = "state ---- int")
	public int editProcess(//
			@P(t = "processId") Long id, //
			@P(t = "流程定义编号") Long pdId, //
			@P(t = "流程标题") String title, //
			@P(t = "流程节点编号") Long currActivityId, //
			String remark//
	) throws ServerException {
		Process pro = new Process();

		pro.title = title;
		pro.currActivityId = currActivityId;
		pro.timestamp = new Date();
		pro.remark = remark;

		return processRepository.updateByANDKeys(conn, new String[] { "pd_id", "id" }, new Object[] { pdId, id }, pro,
				true);
	}

	/*
	 * 删除流程实例
	 */
	@POSTAPI(//
			path = "deleteProcess", //
			des = "删除Process", //
			ret = "state ---- int")
	// 逻辑删除
	public int deleteProcess(//
			@P(t = "processId ") Long id//
	) throws ServerException {
		return processRepository.deleteByKey(conn, "id", id);
	}

	/*
	 * 通过pdid查询下属process
	 */
	@POSTAPI(//
			path = "getProcessListByPDId", //
			des = "通过pdid查询下属process", //
			ret = "List<Process>")
	public List<Process> getProcessListByPDId(//
			@P(t = "流程定义编号Definition") Long pdid, //
			Integer count, //
			Integer offset//
	) throws ServerException {
		return processRepository.getListByKey(conn, "pd_id", pdid, count, offset);
	}

	/*
	 * 通过processid查询process
	 */
	@POSTAPI(//
			path = "getProcessById", //
			des = "通过processid精确查询process", //
			ret = "Process")
	public Process getProcessById(//
			@P(t = "processId") Long id//
	) throws ServerException {
		return processRepository.getByKey(conn, "id", id);
	}

	// TODO 将来实现，根据用户编号获取他创建的Process
	public Process getProcessByUserId(//
	) throws ServerException {
		return null;
	}

	/*
	 * processLogService
	 */
	/*
	 * 创建processLog
	 */
	@POSTAPI(//
			path = "addProcessLog", //
			des = "增加processLog"//
	)
	public void addProcessLog(//
			@P(t = "process流程编号") Long processId, //
			@P(t = "LogTitle标题") String title, //
			@P(t = "使用者编号") Long userid, //
			@P(t = "使用者名称") String userName, //
			@P(t = "行为或活动") String action, //
			@P(t = "行为或活动说明") String actionDesc, //
			@P(t = "记录扩展数据") JSONObject ext//
	) throws ServerException {
		ProcessLog pl = new ProcessLog();
		Long id = IDUtils.getSimpleId();
		pl.processId = processId;
		pl.id = id;
		pl.title = title;
		pl.type = ProcessLog.TYPE_INFO;
		pl.userId = userid;
		pl.userName = userName;
		pl.action = action;
		pl.actionDesc = actionDesc;
		pl.ext = ext;
		processLogRepository.insert(conn, pl);
	}

	/*
	 * 查询一个processid的所有processlog数据
	 */
	@POSTAPI(//
			path = "getProcessLogListByProcessId", //
			des = "查询一个processid的所有processlog数据", //
			ret = "List<ProcessLog>")
	public List<ProcessLog> getProcessLogListByProcessId(//
			@P(t = "processId流程编号") Long processId, //
			Integer count, //
			Integer offset//
	) throws ServerException {
		return processLogRepository.getListByKey(conn, "process_id", processId, count, offset);
	}

	/*
	 * ModuleService
	 */
	private static HashMap<Long, Module> SYS_MODULE_MAP = new HashMap<>();

	public static ArrayList<Module> SYS_MODULE_LIST = new ArrayList<>();

	static {
		// 添加module到系统中
		SYS_MODULE_MAP.put(Module.default_flow.id, Module.default_flow);

		Iterator<Module> it = SYS_MODULE_MAP.values().iterator();
		while (it.hasNext()) {
			SYS_MODULE_LIST.add(it.next());
		}
	}

	/*
	 * 创建自定义Module
	 */
	@POSTAPI(//
			path = "createModule", //
			des = "创建自定义Module"//
	)
	public void createModule(@P(t = "自定义module名称") String name//
	) throws ServerException {
		Long id = IDUtils.getSimpleId();
		Module mod = new Module();
		mod.id = id;
		mod.name = name;
		moduleRepository.insert(conn, mod);
	}

	/*
	 * 编辑自定义module
	 */
	@POSTAPI(//
			path = "editModule", //
			des = "编辑自定义module", //
			ret = "state ---- int")
	public int editModule(//
			@P(t = "module编号") Long id, //
			@P(t = "自定义module名称") String name//
	) throws ServerException {
		Module renew = new Module();
		renew.name = name;

		return moduleRepository.updateByKey(conn, "id", id, renew, true);
	}

	/*
	 * 查询所有module
	 */
	@POSTAPI(//
			path = "getModuleList", //
			des = "查询所有Module", //
			ret = "List<Module>"//
	)
	public List<Module> getModuleList(//
			Integer count, //
			Integer offset//
	) throws ServerException {
		return moduleRepository.getList(conn, count, offset);
	}

	/*
	 * 通过moduleId 查询module
	 */
	@POSTAPI(//
			path = "getModuleById", //
			des = "通过moduleId 查询module", //
			ret = "List<Module>"//
	)
	public Module getModuleById(//
			@P(t = "module编号") Long id//
	) throws ServerException {
		return moduleRepository.getByKey(conn, "id", id);
	}

	/*
	 * processAsset
	 */

	/*
	 * 创建processAsset
	 */
	@POSTAPI(//
			path = "createProcessAsset", //
			des = "创建processAsset附件关系"//
	)
	public void createProcessAsset(//
			@P(t = "文件归属对象类型（属于Activity或者是属于Definition）") Byte type, //
			@P(t = "ActivityId 或 DefinitionId") Long ownerId, //
			@P(t = "附件关系名称") String name, //
			@P(t = "附件编号") Long annexId//
	) throws ServerException {
		Long id = IDUtils.getSimpleId();

		ProcessAsset proA = new ProcessAsset();
		proA.type = ProcessAsset.TYPE_ACTIVITY;
		proA.ownerId = ownerId;
		proA.id = id;
		proA.name = name;
		proA.annexId = annexId;

		processAssetRepository.insert(conn, proA);

	}

	@POSTAPI(//
			path = "editProcessAsset", //
			des = "编辑processAsset附件关系", //
			ret = "state --- int"//
	)
	public int editProcessAsset(//
			@P(t = "asset编号") Long assetId, //
			@P(t = "文件归属对象类型（属于Activity或者是属于Definition）") Byte type, //
			@P(t = "ActivityId 或 DefinitionId") Long ownerId, //
			@P(t = "附件关系名称") String name, //
			@P(t = "附件编号") Long annexId//
	) throws ServerException {
		ProcessAsset renew = new ProcessAsset();
		renew.name = name;
		renew.annexId = annexId;

		return processAssetRepository.updateByKey(conn, "id", assetId, renew, true);
	}

	@POSTAPI(//
			path = "getProcessAssetByOwnerId", //
			des = "通过OwnerId得到processAsset附件关系列表", //
			ret = "List<ProcessAsset>"//
	)
	public List<ProcessAsset> getProcessAssetByOwnerId(//
			@P(t = "ActivityId 或 DefinitionId") Long ownerId, //
			Integer count, //
			Integer offset//
	) throws ServerException {
		return processAssetRepository.getListByKey(conn, "owner_id", ownerId, count, offset);
	}

	@POSTAPI(//
			path = "delProcessAsset", //
			des = "删除processAsset附件关系数据", //
			ret = "state --- int"//
	)
	public int delProcessAsset(//
			@P(t = "assetId") Long assetId//
	) throws ServerException {
		return processAssetRepository.deleteByKey(conn, "id", assetId);
	}
}
