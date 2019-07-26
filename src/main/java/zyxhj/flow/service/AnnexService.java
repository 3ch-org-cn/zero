package zyxhj.flow.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSONObject;

import zyxhj.flow.domain.Annex;
import zyxhj.flow.repository.AnnexRepository;
import zyxhj.utils.IDUtils;
import zyxhj.utils.api.Controller;
import zyxhj.utils.data.DataSource;

public class AnnexService extends Controller {

	private static Logger log = LoggerFactory.getLogger(AnnexService.class);

	private AnnexRepository annexRepository;
	
	private DruidPooledConnection conn;

	public AnnexService(String node) {
		super(node);
		try {
			conn = DataSource.getDruidDataSource("rdsDefault.prop").getConnection();

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	@POSTAPI(//
			path = "createAnnex", //
			des = "创建附件" //
	)
	public void createAnnex(//
			@P(t = "附件持有者编号") Long ownerId, //
			@P(t = "附件名称") String name, //
			@P(t = "类型") Byte type, //
			@P(t = "数据内容，JSONObject") JSONObject data, //
			@P(t = "数据内容，JSONObject") JSONObject tags //
	) throws Exception {
		Annex a = new Annex();
		Long id = IDUtils.getSimpleId();
		a.ownerId = ownerId;
		a.id = id;
		a.name = name;
		a.createTime = new Date();
		a.type = type;
		a.data = data;
		a.tags = tags;

		annexRepository.insert(conn, a);
	}

	@POSTAPI(//
			path = "delAnnex", //
			des = "删除附件", //
			ret = "state --- int"
	)
	public int delAnnex(//
			@P(t = "附件持有者编号") Long ownerId, //
			@P(t = "附件编号") Long id//
	) throws Exception {
		return annexRepository.deleteByANDKeys(conn, new String[] {"owner_id", "id"}, new Object[] {ownerId, id});
	}

	@POSTAPI(//
			path = "editAnnex", //
			des = "修改附件", //
			ret = "state --- int"
	)
	public int editAnnex(//
			@P(t = "附件持有者编号") Long ownerId, //
			@P(t = "附件编号") Long id, //
			@P(t = "附件名称") String name, //
			@P(t = "类型") Byte type, //
			@P(t = "数据内容，JSONObject") JSONObject data, //
			@P(t = "数据内容，JSONObject") JSONObject tags //
	) throws Exception {

		Annex a = new Annex();
		a.ownerId = ownerId;
		a.id = id;
		a.name = name;
		a.type = type;
		a.data = data;
		a.tags = tags;

		return annexRepository.updateByANDKeys(conn, new String[] {"owner_id", "id"}, new Object[] {ownerId, id}, a, true);
	}

	@POSTAPI(//
			path = "getAnnexList", //
			des = "根据ownerId获取Annex列表", //
			ret = "Annex列表"//
	)
	public List<Annex> getAnnexList(//
			@P(t = "附件持有者编号") Long ownerId, //
			Integer count, //
			Integer offset//
	) throws Exception {
		return annexRepository.getListByKey(conn, "owner_id", ownerId, count, offset);
	}

	@POSTAPI(//
			path = "getAnnexById", //
			des = "根据主键获取Annex", //
			ret = "Annex对象"//
	)
	public Annex getAnnexById(//
			@P(t = "附件持有者编号") Long ownerId, //
			@P(t = "附件编号") Long id//
	) throws Exception {
		return annexRepository.getByANDKeys(conn, new String[] {"owner_id","id"}, new Object[] { ownerId,id});
	}
	
}