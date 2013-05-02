package org.cloudbus.cloudsim.ex.mapreduce.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.ex.mapreduce.models.cloud.Cloud;
import org.cloudbus.cloudsim.ex.mapreduce.models.cloud.PrivateCloudDatacenter;
import org.cloudbus.cloudsim.ex.mapreduce.models.cloud.PublicCloudDatacenter;
import org.cloudbus.cloudsim.ex.mapreduce.models.cloud.VmInstance;
import org.cloudbus.cloudsim.ex.mapreduce.models.cloud.VmType;
import org.cloudbus.cloudsim.ex.mapreduce.models.request.MapTask;
import org.cloudbus.cloudsim.ex.mapreduce.models.request.ReduceTask;
import org.cloudbus.cloudsim.ex.mapreduce.models.request.Request;
import org.cloudbus.cloudsim.ex.mapreduce.models.request.Task;

public class BruteForce extends Policy {

	@Override
	public Boolean runAlgorithm(Cloud cloud, Request request) {
		// Fill nVMs
		List<VmInstance> nVMs = new ArrayList<VmInstance>();
		int numTasks = request.job.mapTasks.size()
				+ request.job.reduceTasks.size();
		for (PublicCloudDatacenter publicCloudDatacenter : cloud.publicCloudDatacenters) {
			for (VmType vmType : publicCloudDatacenter.vmTypes)
				for (int i = 0; i < numTasks; i++)
					nVMs.add(new VmInstance(vmType));

		}
		for (PrivateCloudDatacenter privateCloudDatacenter : cloud.privateCloudDatacenters) {
			VmType firstVmType = privateCloudDatacenter.vmTypes.get(0);
			int maxAvailableResource = privateCloudDatacenter
					.getMaxAvailableResource(firstVmType);

			for (int i = 0; i < Math.min(numTasks, maxAvailableResource); i++)
				nVMs.add(new VmInstance(firstVmType));

		}
		//Temporary Add all VMs to the request
		request.mapAndReduceVmProvisionList = nVMs;

		// Fill rTasks
		List<Task> rTasks = new ArrayList<Task>();
		for (MapTask mapTask : request.job.mapTasks)
			rTasks.add(mapTask);
		for (ReduceTask reduceTask : request.job.reduceTasks)
			rTasks.add(reduceTask);

		// Get permutations
		List<Integer[]> nPr = getPermutations(nVMs.size(), rTasks.size());

		// Fill ExecutionPlans
		List<ExecutionPlan> executionPlans = new ArrayList<ExecutionPlan>();

		// Fill all schedulingPlans
		for (Integer[] one_nPr : nPr) {
			ExecutionPlan one_ExecutionPlan = new ExecutionPlan();
			for (int i = 0; i < one_nPr.length; i++)
			{
				int TaskId = rTasks.get(i).getCloudletId();
				int VmId = nVMs.get(one_nPr[i]-1).getId();
				one_ExecutionPlan.one_nPrSchedulingPlan.put(TaskId, VmId);
			}
			executionPlans.add(one_ExecutionPlan);
		}

		// Get all Execution Times and Costs for each SchedulingPlan
		for (ExecutionPlan executionPlan : executionPlans) {
			request.schedulingPlan = executionPlan.one_nPrSchedulingPlan;
			for (int i = 0; i < request.job.mapTasks.size(); i++)
				executionPlan.ExecutionTime += request.job.mapTasks.get(i)
						.getTotalTime();
			for (int i = 0; i < request.job.reduceTasks.size(); i++)
				executionPlan.ExecutionTime += request.job.reduceTasks.get(i)
						.getTotalTime();

			for (int i = 0; i < request.job.mapTasks.size(); i++)
				executionPlan.Cost += request.job.mapTasks.get(i)
						.getTotalCost();
			for (int i = 0; i < request.job.reduceTasks.size(); i++)
				executionPlan.Cost += request.job.reduceTasks.get(i)
						.getTotalCost();
		}

		// Select the fastest
		double fastest = executionPlans.get(0).ExecutionTime;
		for (ExecutionPlan executionPlan : executionPlans)
			if (executionPlan.ExecutionTime < fastest)
				fastest = executionPlan.ExecutionTime;

		// Collect the candidate ExecutionPlans, where they are fastest
		double margin = 0.0;
		List<ExecutionPlan> candidateExecutionPlans = new ArrayList<ExecutionPlan>();
		for (ExecutionPlan executionPlan : executionPlans)
			if (executionPlan.ExecutionTime - margin <= fastest)
				candidateExecutionPlans.add(executionPlan);

		// Select the cheapest from the fastest
		ExecutionPlan cheapestFastestExecutionPlan = candidateExecutionPlans
				.get(0);
		for (ExecutionPlan executionPlan : candidateExecutionPlans)
			if (executionPlan.Cost < cheapestFastestExecutionPlan.Cost)
				cheapestFastestExecutionPlan = executionPlan;

		// Selected SchedulingPlan
		Map<Integer, Integer> selectedSchedulingPlan = cheapestFastestExecutionPlan.one_nPrSchedulingPlan;

		// 1- Provisioning
		request.mapAndReduceVmProvisionList = new ArrayList<VmInstance>(); //To remove the temporary VMs
		
		for (Map.Entry<Integer, Integer> entry : selectedSchedulingPlan.entrySet()) {
			Task task = request.job.getTask(entry.getKey());
			if(task instanceof MapTask)
				for (VmInstance vm : nVMs) {
					if (entry.getValue() == vm.getId())
						if (!request.mapAndReduceVmProvisionList.contains(vm) && !request.reduceOnlyVmProvisionList.contains(vm))
							request.mapAndReduceVmProvisionList.add(vm);
				}
			else
				for (VmInstance vm : nVMs) {
					if (entry.getValue() == vm.getId())
						if (!request.mapAndReduceVmProvisionList.contains(vm) && !request.reduceOnlyVmProvisionList.contains(vm))
							request.reduceOnlyVmProvisionList.add(vm);
				}
		}

		// 2- Scheduling
		request.schedulingPlan = selectedSchedulingPlan;

		return true;
	}

	static ArrayList<Integer> indexOfAll(double obj, List<Double> list) {
		ArrayList<Integer> indexList = new ArrayList<Integer>();
		for (int i = 0; i < list.size(); i++)
			if (obj == list.get(i))
				indexList.add(i);
		return indexList;
	}

	private List<Integer[]> getPermutations(int n, int r) {
		List<Integer[]> permutations = new ArrayList<Integer[]>();

		int[] res = new int[r];
		for (int i = 0; i < res.length; i++) {
			res[i] = 1;
		}
		boolean done = false;
		while (!done) {
			// Convert int[] to Integer[]
			Integer[] resObj = new Integer[r];
			for (int i = 0; i < res.length; i++) {
				resObj[i] = res[i];
			}
			permutations.add(resObj);
			done = getNext(res, n, r);
		}

		// int count = 1;
		// for (Integer[] i : nCr) {
		// System.out.println(count++ + Arrays.toString(i));
		// }

		return permutations;
	}

	// ///////

	private boolean getNext(final int[] num, final int n, final int r) {
		int target = r - 1;
		num[target]++;
		if (num[target] > n) {
			// Carry the One
			while (num[target] >= n) {
				target--;
				if (target < 0) {
					break;
				}
			}
			if (target < 0) {
				return true;
			}
			num[target]++;
			for (int i = target + 1; i < num.length; i++) {
				num[i] = 1;
			}
		}
		return false;
	}

}
