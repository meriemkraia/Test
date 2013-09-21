package org.cloudbus.cloudsim.ex.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.logging.Level;

import org.cloudbus.cloudsim.ex.disk.HddVm;
import org.cloudbus.cloudsim.ex.util.CustomLog;
import org.cloudbus.cloudsim.ex.vm.MonitoredVMex;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;

/**
 * 
 * A load balancer, which compresses the load into the smallest number of VMs
 * whose utilisation is below certain thresholds.
 * 
 * @author nikolay.grozev
 * 
 */
public class CompressLoadBalancer extends BaseWebLoadBalancer implements ILoadBalancer {

    private final CPUUtilisationComparator cpuUtilReverseComparator;
    private final WebBroker broker;
    private final double cpuThreshold;
    private final double ramThreshold;

    private StringBuilder debugSB = new StringBuilder();

    /**
     * Const.
     * 
     * @param appId
     * @param ip
     * @param appServers
     * @param dbBalancer
     * @param cpuThreshold
     *            - the CPU threshold. Must be in the interval [0, 1].
     * @param ramThreshold
     *            - the RAM threshold. Must be in the interval [0, 1].
     */
    public CompressLoadBalancer(WebBroker broker, final long appId, final String ip, final List<HddVm> appServers,
	    final IDBBalancer dbBalancer, double cpuThreshold, double ramThreshold) {
	super(appId, ip, appServers, dbBalancer);
	this.cpuThreshold = cpuThreshold;
	this.ramThreshold = ramThreshold;

	this.broker = broker;
	cpuUtilReverseComparator = new CPUUtilisationComparator();
    }

    @Override
    public void assignToServers(final WebSession... sessions) {
	// Filter all sessions without an assigned application server
	List<WebSession> noAppServSessions = new ArrayList<>(Arrays.asList(sessions));
	for (ListIterator<WebSession> iter = noAppServSessions.listIterator(); iter.hasNext();) {
	    WebSession sess = iter.next();
	    if (sess.getAppVmId() != null) {
		iter.remove();
	    }
	}

	List<HddVm> runingVMs = getRunningAppServers();
	// No running AS servers - log an error
	if (runingVMs.isEmpty()) {
	    for (WebSession session : noAppServSessions) {
		if (getAppServers().isEmpty()) {
		    CustomLog.printf(Level.SEVERE,
			    "Load Balancer(%s): session %d cannot be scheduled, as there are not AS servers",
			    this.broker.toString(),
			    session.getSessionId());
		} else {
		    CustomLog
			    .printf(Level.SEVERE,
				    "[Load Balancer](%s): session %d cannot be scheduled, as all AS servers are either booting or terminated",
				    this.broker.toString(),
				    session.getSessionId());
		}
	    }
	} else {// Assign to one of the running VMs
	    for (WebSession session : noAppServSessions) {
		List<HddVm> vms = new ArrayList<>(runingVMs);
		Set<Integer> usedASServers = this.broker.getUsedASServers();
		cpuUtilReverseComparator.setUsedASServers(usedASServers);
		Collections.sort(vms, cpuUtilReverseComparator);

		// For debug purposes:
		debugSB.setLength(0);
		for (HddVm vm : vms) {
		    debugSB.append(String.format("%s[%s] cpu(%.2f), ram(%.2f), cdlts(%d); ",
			    vm, (usedASServers.contains(vm.getId()) ? "" : "FREE, ") + vm.getStatus(),
			    vm.getCPUUtil(), vm.getRAMUtil(), vm.getCloudletScheduler().getCloudletExecList().size()));
		}

		HddVm hostVM = vms.get(vms.size() - 1);
		for (HddVm vm : vms) {
		    if (vm.getCPUUtil() < cpuThreshold && vm.getRAMUtil() < ramThreshold && !vm.isOutOfMemory()) {
			hostVM = vm;
			break;
		    }
		}

		session.setAppVmId(hostVM.getId());
		CustomLog.printf(
			"[Load Balancer](%s): Assigning sesssion %d to %s[%s] cpu(%.2f), ram(%.2f), cdlts(%d)",
			broker, session.getSessionId(), hostVM, hostVM.getStatus(),
			hostVM.getCPUUtil(), hostVM.getRAMUtil(), hostVM.getCloudletScheduler().getCloudletExecList()
				.size());
		CustomLog.printf("[Load Balancer](%s), Cadidate VMs: %s", broker, debugSB);
	    }

	    // Set the DB VM
	    for (WebSession session : sessions) {
		if (session.getDbBalancer() == null) {
		    session.setDbBalancer(getDbBalancer());
		}
	    }
	}
    }

    private static class CPUUtilisationComparator implements Comparator<MonitoredVMex> {

	private Set<Integer> usedASServers;

	public void setUsedASServers(Set<Integer> usedASServers) {
	    this.usedASServers = usedASServers;
	}

	@Override
	public int compare(final MonitoredVMex vm1, final MonitoredVMex vm2) {
	    if (!usedASServers.contains(vm1.getId()) && !usedASServers.contains(vm2.getId())) {
		return 0;
	    } else if (!usedASServers.contains(vm1.getId())) {
		return 1;
	    } else if (!usedASServers.contains(vm2.getId())) {
		return -1;
	    } else {
		return -Double.valueOf(vm1.getCPUUtil()).compareTo(Double.valueOf(vm2.getCPUUtil()));
	    }
	}
    }

}
