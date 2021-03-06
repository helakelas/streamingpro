package streaming.common.zk

import net.csdn.ServiceFramwork
import net.csdn.common.logging.Loggers
import net.csdn.common.network.NetworkUtils.StackType
import net.csdn.common.settings.ImmutableSettings
import net.csdn.common.settings.ImmutableSettings._
import org.I0Itec.zkclient.IZkDataListener
import streaming.common.ParamsUtil

/**
 * 8/3/16 WilliamZhu(allwefantasy@gmail.com)
 */
object ZkRegister {
  val logger = Loggers.getLogger(classOf[ZkRegister])


  def registerToZk(params: ParamsUtil) = {
    val settingsB: ImmutableSettings.Builder = settingsBuilder()
    settingsB.put(ServiceFramwork.mode + ".zk.conf_root_dir", params.getParam("streaming.zk.conf_root_dir"))
    settingsB.put(ServiceFramwork.mode + ".zk.servers", params.getParam("streaming.zk.servers"))
    val zk = new ZKClient(settingsB.build())
    val client = zk.zkConfUtil.client

    if (!client.exists(ZKConfUtil.CONF_ROOT_DIR)) {
      client.createPersistent(ZKConfUtil.CONF_ROOT_DIR, true);
    }

    if (client.exists(ZKConfUtil.CONF_ROOT_DIR + "/address")) {
      client.delete(ZKConfUtil.CONF_ROOT_DIR + "/address")
      logger.error(s"${ZKConfUtil.CONF_ROOT_DIR} already exits in zookeeper")
    }
    val hostAddress = net.csdn.common.network.NetworkUtils.getFirstNonLoopbackAddress(StackType.IPv4).getHostAddress
    val port = params.getParam("streaming.driver.port", "9003")
    logger.info(s"register ip and port to zookeeper:\n" +
      s"zk=[${params.getParam("streaming.zk.servers")}]\n" +
      s"${ZKConfUtil.CONF_ROOT_DIR}/address=${hostAddress}:${port}")


    val address = ZKConfUtil.CONF_ROOT_DIR + "/address";

    client.createEphemeral(address, hostAddress + ":" + port)

    //if Ephemeral node was removed by zookeeper cause some unexpected reason,we should monitor
    // this event and create the node  again.
    client.subscribeDataChanges(address, new IZkDataListener {
      override def handleDataChange(s: String, o: scala.Any): Unit = {
        // do nothing
      }

      override def handleDataDeleted(s: String): Unit = {
        logger.error(s"${address}=${s} removed by zookeeper, create again")
        client.createEphemeral(address, hostAddress + ":" + port)
      }
    })

    zk
  }
}

class ZkRegister
