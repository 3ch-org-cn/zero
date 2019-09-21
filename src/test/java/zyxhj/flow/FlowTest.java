package zyxhj.flow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import zyxhj.core.domain.Module;
import zyxhj.core.domain.Tag;
import zyxhj.flow.domain.Process;
import zyxhj.flow.domain.ProcessAction;
import zyxhj.flow.domain.ProcessActivity;
import zyxhj.flow.domain.ProcessActivity.Receiver;
import zyxhj.flow.domain.ProcessActivityGroup.SubActivity;
import zyxhj.flow.domain.ProcessActivityGroup;
import zyxhj.flow.domain.ProcessAsset;
import zyxhj.flow.domain.ProcessAssetDesc;
import zyxhj.flow.domain.ProcessDefinition;
import zyxhj.flow.domain.TableSchema;
import zyxhj.flow.service.FlowService;
import zyxhj.flow.service.ProcessService;
import zyxhj.flow.service.TableService;
import zyxhj.utils.IDUtils;
import zyxhj.utils.Singleton;
import zyxhj.utils.data.DataSource;

public class FlowTest {

	private static DruidPooledConnection conn;

	private static FlowService flowService;
	private static TableService tableService;
	private static ProcessService processService;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try {
			conn = DataSource.getDruidDataSource("rdsDefault.prop").getConnection();

			flowService = Singleton.ins(FlowService.class, "sdf");
			tableService = Singleton.ins(TableService.class, "sdf");
			processService = Singleton.ins(ProcessService.class, "sdf");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		conn.close();
	}

	private static Long tableSchemaId = 400705582427294L;
	private static Long pdId = 400705633248957L;

	private static Long activityIdStart = 400719819725379L;
	private static Long activityIdSecond = 400719819748676L;
	private static Long activityIdEnd = 400719819765573L;

	private static Long activityDescId = 400719819841094L;

	private static Long processId = 400722561559259L;

	@Test
	public void flowTest() throws Exception {
		testCreateTableSchema();
	}

	/**
	 * 创建TableSchema
	 */
	@Test
	public void testCreateTableSchema() throws Exception {

		JSONArray columns = new JSONArray();

		for (int i = 0; i < 5; i++) {
			TableSchema.Column tsc = new TableSchema.Column();
			tsc.name = "COL" + i;
			tsc.alias = "第" + i + "列";
			tsc.columnType = TableSchema.Column.COLUMN_TYPE_DATA;
			tsc.dataType = TableSchema.Column.DATA_TYPE_INTEGER;
			tsc.necessary = true;

			JSONObject jo = new JSONObject();
			jo.put(tsc.name, tsc);
			columns.add(jo);
		}

		TableSchema.Column tscTotal = new TableSchema.Column();
		tscTotal.name = "TOTAL1";
		tscTotal.alias = "合计1";
		tscTotal.columnType = TableSchema.Column.COLUMN_TYPE_COMPUTE;
		tscTotal.dataType = TableSchema.Column.DATA_TYPE_INTEGER;
		tscTotal.computeFormula = "{{COL1}} + {{COL2}} + {{COL3}} + {{COL4}} + {{COL5}}";

		JSONObject jo = new JSONObject();
		jo.put(tscTotal.name, tscTotal);
		columns.add(jo);

		// 为表定义添加标签

		JSONArray tag = new JSONArray();

		tag.add(Tag.SYS_TABLE_SCHEMA_DATA);
		tag.add(Tag.SYS_TABLE_SCHEMA_APPLICATION);

		TableSchema ts = tableService.createTableSchema("表的别名", TableSchema.TYPE_VIRTUAL_QUERY_TABLE, columns, tag);
		System.out.println(JSON.toJSONString(ts));
		System.out.println("--- TableSchema ok ---");

	}

	/**
	 * 创建ProcessDefinition
	 */
	@Test
	public void testCreatePD() throws Exception {
		JSONArray tags = new JSONArray();
		tags.add("测试1");
		tags.add("测试2");

		JSONArray lanes = new JSONArray();
		lanes.add("测试泳道名称1");
		lanes.add("测试泳道名称2");

		ProcessDefinition pd = flowService.createPD(Module.FLOW.key, "testPD", tags, lanes);
		System.out.println(JSON.toJSONString(pd));
		System.out.println("--- ProcessDefinition ok ---");
	}

	/**
	 * 创建多个Activity，并设置PD的起点和终点
	 */
	@Test
	public void testCreatActivityANDSetStartEndANDSetAssetDesc() throws Exception {

		List<Receiver> receivers = new ArrayList<ProcessActivity.Receiver>();

		Receiver r = new Receiver();
		r.type = Receiver.TYPE_DEPARTMENT;
		r.id = IDUtils.getSimpleId();
		r.label = "测试部门";
		r.remark = "单元测试Receiver数据";

		receivers.add(r);

		List<ProcessActivity> activitys = new ArrayList<>();
		for (int i = 0; i < 3; i++) {

			ProcessActivity pa = flowService.createPDActivity(pdId, StringUtils.join("activity>", i), "测试泳道名称1",
					JSON.toJSONString(receivers), null);
			activitys.add(pa);
		}
		System.out.println("--- create Activity ok ---");

		flowService.setPDStartActivity(pdId, activitys.get(0).id);
		flowService.setPDEndActivity(pdId, activitys.get(activitys.size() - 1).id);
		System.out.println("--- set start end Activity ok ---");

		ProcessDefinition pd = flowService.getPDById(pdId);
		flowService.createAssetDesc(pd.startActivityId, ProcessAssetDesc.TYPE_TABLE, "测试审批", true, "", "",
				activityIdStart.toString());
		System.out.println("--- set AssetDesc ok ---");
	}

	@Test
	public void testCreatAssetDescANDAction() throws Exception {

		List<ProcessAction> actions = new ArrayList<ProcessAction>();

		ProcessAction a = new ProcessAction();
		a.id = IDUtils.getSimpleId();
		a.label = "测试提交";
		a.type = ProcessAction.TYPE_ACCEPT;

		JSONArray arr = new JSONArray();

		JSONObject expDefault = new JSONObject();
		expDefault.put("exp", "expDefault");
		expDefault.put("target", activityIdEnd.toString());

		JSONObject exp2 = new JSONObject();
		// "getTableField(tableSchemaId,fieldName,tableDataId) > 3"
		exp2.put("exp", "getTableField(tableSchemaId,fieldName,tableDataId) > 3");
		exp2.put("target", activityIdSecond.toString());

		arr.add(expDefault);
		arr.add(exp2);

		a.rules = arr;
		String strRules = JSON.toJSONString(arr);
		System.out.println(strRules);

		actions.add(a);

		flowService.editPDActivity(pdId, activityIdStart, null, null, null, JSON.toJSONString(actions));
		System.out.println("--- set Set Action ok ---");
	}

	@Test
	public void testCreatProcess() throws Exception {

		Process p = processService.createProcess(pdId, 1L,"这事一个测试", "我要测试");
		System.out.println("--- set Set Action ok ---");

	}

	@Test
	public void testInsertTableData() throws Exception {
		JSONObject jo = new JSONObject();
		// 根据table的schema来填写数据
		jo.put("COL1", 10);
		jo.put("COL2", 20);
		jo.put("COL3", 34);
		jo.put("COL4", 234);
		jo.put("COL5", 123);
		ProcessAsset pa = processService.insertProcessTableData(123L, processId, activityDescId, tableSchemaId, "", jo);
		System.out.println("--- insertData ok ---");
	}

	@Test
	public void testExecuteAction() throws Exception {

		// [{"id":"16c73e50dd437","label":"测试提交","rules":[{"exp":"expDefault","target":"400719819765573"},{"exp":"getTableField(tableSchemaId,fieldName,tableDataId)
		// > 3","target":"400719819748676"}],"type":"accept"}]

		processService.executeProcessAction(processId, activityIdStart, "16c73e50dd437", 123L);
	}

	@Test
	public void testGetProcessAssetByDescIds() throws Exception {

		JSONArray ja = new JSONArray();
		ja.add(400719819841094L);
		ja.add(400719819841234L);
		ja.add(400719823441234L);

		List<ProcessAsset> paList = processService.getProcessAssetByDescIds(processId, ja, 10, 0);

		System.out.println(JSON.toJSONString(paList));
	}

	@Test
	public void testGetAssetByProcessIdAndUserId() throws Exception {

		JSONObject processAsset = processService.getProcessAssetByIdANDUserId(222L, 400792320906344L);

		System.out.println(processAsset);
	}

	@Test
	public void editProcessTableData() throws Exception {
		JSONObject data = JSONObject.parseObject("{star: 1565625600000, name: \"啦啦啦啦111\", end: 1565712000000}");
		processService.editProcessTableData(400792274247519L, 400809850254116L, data);

	}

	//创建流程定义   pdId = 401122871418288
	@Test
	public void testCreatePDefinition() throws Exception {
		String title = "测试申请流程定义";
		JSONArray tags = new JSONArray();
		tags.add("测试1");
		tags.add("测试2");

		JSONArray lanes = new JSONArray();
		lanes.add("测试泳道名称1");
		lanes.add("测试泳道名称2");

		flowService.createPD(Module.FLOW.key, title, tags, lanes);
	}
	
	
	//创建节点
	/**
	 * 第六个节点   401127889022941
	 * 第五个节点   401127885477213
	 * 第四个节点   401127881993223
	 * 第三个节点   401127873928902
	 * 第二个节点   401127871000279
	 * 第一个节点   401127867024131
	 * 
	 */
			
	@Test
	public void testCreateActivity() throws Exception {
		List<Receiver> receivers = new ArrayList<ProcessActivity.Receiver>();

		Receiver r = new Receiver();
		r.type = Receiver.TYPE_USER;
		r.id = IDUtils.getSimpleId();
		r.label = "测试权限用户";
		r.remark = "单元测试Receiver数据";

		receivers.add(r);
		
		flowService.createPDActivity(401122871418288L, "第六个节点", "测试泳道", JSON.toJSONString(receivers), null);
	}
	
	
	
	// 创建节点分组
	@Test
	public void testCreateActivityGroup() throws Exception {
		JSONObject jo = new JSONObject();
		jo.put("size", new int[] { 70, 40 });
		jo.put("shape", "round-rect");
		jo.put("x", 532);
		jo.put("y", 488);

		JSONObject jo1 = new JSONObject();
		jo1.put("fill", "#fff");
		jo1.put("radius", 5);
		jo1.put("stroke", "#1890ff");

		jo.put("style", jo1);
		jo.put("id", 400969407422093L);
		jo.put("label", "常规节点");

		ProcessActivityGroup pag = flowService.createProcessActivityGroup(401122871418288L, "测试流程节点分组", "", jo);

	}

	// 添加一个节点到节点分组中 //第三、四、五个节点
	@Test
	public void testAddSubActivity() throws Exception {
		int ret = flowService.putActivityInGroup(401127906929613L, 401127885477213L, true);
		System.out.println(ret);
	}

	// 从节点分组中移除一个节点
	@Test
	public void testRemaveSubActivity() throws Exception {
		int ret = flowService.removeActivityInGroup(401123305577518L, 400969404882060L);
		System.out.println(ret);
	}

	// 得到节点分组中的所有节点编号
	@Test
	public void testGetALLSubActivity() throws Exception {
		Long activityGroupId = 401127906929613L;
		List<ProcessActivity> slist = flowService.getSubActivity(activityGroupId);
		for (int i = 0; i < slist.size(); i++) {
			System.out.println(slist.get(i).id);
		}
	}

	@Test
	public void testUpdateSubActivity() throws Exception {
		Long activityGroupId = 401127906929613L;
		Boolean necessary = false;
		Long activityId = 400969407422093L;
		int ret = flowService.editSubActivity(activityGroupId, activityId, necessary);
		System.out.println(ret);
	}

	// 创建action行为
	@Test
	public void testCreateAction1() throws Exception {
		JSONArray roles = new JSONArray();
		JSONObject jo = new JSONObject();
		jo.put("exp", "expDefault");
		jo.put("targetType", "activity");
		jo.put("target", 401628385491116L);// 下一节点 第二节点
		roles.add(jo);
		Long pdId = 401628373028011L;
		Long activityId = 401628390774958L;// 当前节点  第一个节点

		ProcessAction paction = flowService.createProcessAction(pdId, activityId, ProcessAction.OWNER_TYPE_ACTIVITY, ProcessAction.TYPE_REJECT, roles);
	}

	@Test
	public void testCreateAction2() throws Exception {
		JSONArray roles = new JSONArray();
		JSONObject jo = new JSONObject();
		jo.put("exp", "expDefault");
		jo.put("targetType", "activityGroup");
		jo.put("target", 401127906929613L);// 下一节点,节点分组
		roles.add(jo);

		Long pdId = 401122871418288L;
		Long activityId = 401127871000279L;// 当前节点 第二个节点

		ProcessAction paction = flowService.createProcessAction(pdId, activityId, ProcessAction.OWNER_TYPE_ACTIVITY, ProcessAction.TYPE_ACCEPT, roles);
	}

	// 为 节点分组 添加行为动作，同意和驳回
	@Test
	public void testCreateAction3() throws Exception {
		// 同意
		JSONArray roles1 = new JSONArray();
		JSONObject jo1 = new JSONObject();
		jo1.put("exp", "expDefault");
		jo1.put("targetType", "activity");
		jo1.put("target", 401274745109938L);// 下一节点 第六个节点
		roles1.add(jo1);

		// 驳回
		JSONArray roles2 = new JSONArray();

		JSONObject jo2 = new JSONObject();
		jo2.put("exp", "expDefault");
		jo2.put("targetType", "activity");
		jo2.put("target", 401274724523438L);// 上一节点 第二个节点
		roles2.add(jo2);

		Long pdId = 401274709271725L;
		Long activityId = 401274733715119L;// 当前节点分组

		flowService.createProcessAction(pdId, activityId, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_ACCEPT, roles1);
		flowService.createProcessAction(pdId, activityId, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_REJECT, roles2);
	}
	
	/**
	 *	 为节点分组中的节点添加Action行为
	 */
		@Test
		public void testCreateAction4() throws Exception {
			// 同意
			JSONArray roles1 = new JSONArray();
			JSONObject jo1 = new JSONObject();
			jo1.put("exp", "expDefault");
			jo1.put("targetType", "activity");
			jo1.put("target", "null");
			roles1.add(jo1);

			// 驳回
			JSONArray roles2 = new JSONArray();

			JSONObject jo2 = new JSONObject();
			jo2.put("exp", "expDefault");
			jo2.put("targetType", "activity");
			jo2.put("target", "null");
			roles2.add(jo2);

			Long pdId = 401274709271725L;
			Long activityId4 = 401274737184176L;// 第四个节点
			Long activityId5 = 401274739926705L;// 第五个节点

			flowService.createProcessAction(pdId, activityId4, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_ACCEPT, roles1);
			flowService.createProcessAction(pdId, activityId4, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_REJECT, roles2);
			

			flowService.createProcessAction(pdId, activityId5, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_ACCEPT, roles1);
			flowService.createProcessAction(pdId, activityId5, ProcessAction.OWNER_TYPE_ACTIVITY_GROUP, ProcessAction.TYPE_REJECT, roles2);
		}
	
	
	
	/**
	 * 流程实例
	 */
	private static Long pid = 401141089162204L;
	//创建流程实例 --- 成功
	@Test
	public void testCreateProcess() throws Exception {
		Long pdId = 401628373028011L;
		Long userId = 125469853421023L;
		processService.createProcess(pdId, userId, "分户流程测试", "分户流程测试");
	}
	
	//得到process相关信息、processDefinition processActivity processAction
	@Test // --- 成功
	public void testGetProcessInfo() throws Exception {
		Long userId = 125469853421023L;
		JSONObject jo = processService.getProcessInfo(userId, pid);
		System.out.println(jo.toJSONString());
	}
	
	

}
