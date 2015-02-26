import java.util.ArrayList;

import jmetal.core.Problem;
import jmetal.core.Solution;
import jmetal.core.Variable;
import jmetal.encodings.solutionType.IntSolutionType;
import jmetal.encodings.variable.Int;
import jmetal.util.JMException;


public class VMProblem extends Problem{

	/**
	 * 
	 */
	private static final long serialVersionUID = -4810592646779691595L;
	private double P_S_PEAK[];							// Peak power of a generic CPU [W]
	private double P_S_IDLE[];							// Fixed amount of power used by CPUs [W]
	private double P_TS_PEAK=200; 						// Peak power of a top of rack switch [W]
	private double P_TS_IDLE = 0.80* P_TS_PEAK; 		// Fixed amount of power used by top of rack switches [W]
	private double P_AGG_PEAK= 2500; 					// Peak power of an aggregation switch [W]
	private double P_AGG_IDLE=0.80*P_AGG_PEAK; 			// Idle aggregation swithc power [w]
	
	private double maxCPU[];							
	private double maxMEMORY[];						// Total amount of volatile memory in servers [B]
	private double maxDISK[];							// Total amount of mass memory in servers [B]
	
	
	public static int SERV_NUM;						// Number of servers
	private static int SERV_ON_RACK;				// Number of servers in a rack
	private static int RACK_NUM; 					// Number of racks
	private static int RACK_ON_POD; 				// Number of racks in a pod
	private static int POD_NUM ; 					// Number of pods
	
	private double SERVER_LINK_CAPACITY[];
	private double RACK_LINK_CAPACITY[];
	private double serverBWconstraint;
	private double rackBWconstraint;
	private double serverCPUconstraint;
	private double serverMEMconstraint;
	private double serverDISKconstraint;
	
	private int violatedCPUconstraint;
	private int violatedMEMconstraint;
	private int violatedDISKconstraint;
	
	private boolean minAusiliaryObj;
	private boolean maxAusiliaryObj;
	private boolean constrObj;
	ArrayList<VM>vm;
	ServerAllocation[] serverAllocation;
	
	public VMProblem(int task, int server, int servOnRack, int rackOnPod, ArrayList<VM> vm,int instance){
		numberOfObjectives_ = 2;
		numberOfConstraints_ = 2+3*server;
		problemName_ = "VMProblem";
		solutionType_ = new IntSolutionType(this);
		numberOfVariables_ = task;
		SERV_NUM = server;
		SERV_ON_RACK = servOnRack;
		RACK_ON_POD = rackOnPod;
		RACK_NUM = (SERV_NUM / SERV_ON_RACK)+ (SERV_NUM % SERV_ON_RACK == 0 ? 0 : 1);
	    POD_NUM =  (RACK_NUM / RACK_ON_POD) +(RACK_NUM % RACK_ON_POD==0 ? 0 : 1);
	    upperLimit_ = new double[numberOfVariables_];
		lowerLimit_ = new double[numberOfVariables_];
		this.vm = vm;
		serverAllocation=new ServerAllocation[SERV_NUM];
		for (int i = 0; i < numberOfVariables_; ++i) {

			upperLimit_[i] = SERV_NUM-1;

			lowerLimit_[i] = 0;
		}
		
		SERVER_LINK_CAPACITY= new double[SERV_NUM];
		P_S_PEAK=new double [SERV_NUM];
		P_S_IDLE=new double [SERV_NUM];
		maxCPU	= new double [SERV_NUM];
		maxMEMORY = new double [SERV_NUM];
		maxDISK = new double[SERV_NUM];
		RACK_LINK_CAPACITY= new double[RACK_NUM];
		for(int i=0; i< SERV_NUM; ++i){
			SERVER_LINK_CAPACITY[i]=1e9;
			P_S_PEAK[i]=300;
			P_S_IDLE[i]=100;
			
			//VALORI TEMPORANEI
			maxMEMORY[i]=100;
			maxDISK[i]= 100;
			maxCPU[i]=100;
			//fine valori
			serverAllocation[i]=new ServerAllocation(maxCPU[i],maxMEMORY[i],maxCPU[i]);
			
		}
		for(int i=0; i< RACK_NUM; ++i){
			RACK_LINK_CAPACITY[i]=20e9;
		}
		serverBWconstraint=0;
		rackBWconstraint=0;
		serverCPUconstraint=0;
		serverMEMconstraint=0;
		serverDISKconstraint=0;
		
		violatedCPUconstraint=0;
		violatedMEMconstraint=0;
		violatedDISKconstraint=0;
		
		if(instance==0){
			minAusiliaryObj=false;
			maxAusiliaryObj=false;
			constrObj =false;
			
		}else if(instance==1){
			minAusiliaryObj=false;
			maxAusiliaryObj=true;
			numberOfObjectives_= 2 + 3;
			constrObj =false;
		}else if(instance==2){
			minAusiliaryObj=true;
			maxAusiliaryObj=true;
			numberOfObjectives_= 2 + 3 + 3;
		}else if (instance == 3){
			constrObj =true;
			minAusiliaryObj=false;
			maxAusiliaryObj=false;
			numberOfObjectives_= 2 + 3;
		}
		
	}
	
	public int sgn(double x) {
		return x > 0 ? 1 : 0;
	}
	
	@Override
	public void evaluate(Solution solution) throws JMException {
		//TO BE DONE: Memory constraint to be implemented.
		
		
		
		
		Variable[] var = solution.getDecisionVariables();
		int varnum=var.length;
		double cpu[]=new double [SERV_NUM];
		double memory[] = new double [SERV_NUM];
		double disk[]= new double [SERV_NUM];
		double server_power_consumption = 0;
		double switch_power_consumption=0;
		double serverExecutionTime[]=new double [SERV_NUM];
		double bandwidth_per_server[] = new double[SERV_NUM];
		double bandwidth_per_rack[] = new double[RACK_NUM];
		double bandwidth_per_pod[] = new double[POD_NUM];
		
		
		serverBWconstraint=0;
		rackBWconstraint=0;
		
		serverCPUconstraint=0;
		serverMEMconstraint=0;
		serverDISKconstraint=0;
		
		violatedCPUconstraint=0;
		violatedDISKconstraint=0;
		violatedMEMconstraint=0;
		for(int i =0;i <SERV_NUM;++i){
			serverAllocation[i].reset();
		}
		
		for(int i =0 ; i <varnum ; ++i)
		{
			int server = (int)((Int)var[i]).getValue();
			VM current = vm.get(i);
			/*
			cpu[server]+=current.getCpu();
			*/
			
			memory[server]+=current.getMemory();
			disk[server]+=current.getDisk();
			/*
			serverExecutionTime[server]+=current.getMinimumExecutionTime()/current.getCpu();
			/*/
			serverAllocation[server].addTask(current.getMinimumExecutionTime(),current.getCpu(),current.getMemory(),current.getDisk());
			
			double currentBand=current.getBandwidth();
			double serverBand=currentBand;
			double rackBand=currentBand;
			bandwidth_per_server[server]+=currentBand;
			if(bandwidth_per_server[server]>SERVER_LINK_CAPACITY[server]){
				currentBand=bandwidth_per_server[server]-SERVER_LINK_CAPACITY[server];
				/*
				 * Se prima di aggiungere questa banda il link non era saturato allora
				 * la parte che satura il riempie il link viene aggiunta. In caso contrario
				 * non verrà aggiunto niente.
				 */
				bandwidth_per_server[server]=SERVER_LINK_CAPACITY[server];	
				if(currentBand!=0)
				serverBWconstraint+=currentBand;
				else
				serverBWconstraint+=serverBand;
			}
			bandwidth_per_rack[server / SERV_ON_RACK]+=currentBand;
			if(bandwidth_per_rack[server / SERV_ON_RACK]>RACK_LINK_CAPACITY[server / SERV_ON_RACK]){
				currentBand=bandwidth_per_rack[server / SERV_ON_RACK]-RACK_LINK_CAPACITY[server / SERV_ON_RACK];
				/*
				 * Stessa cosa è valida per il rack
				 */
				bandwidth_per_rack[server / SERV_ON_RACK]=RACK_LINK_CAPACITY[server / SERV_ON_RACK];
				if(currentBand!=0)
				rackBWconstraint+=currentBand;
				else
				rackBWconstraint+=rackBand;
			}
			/*
			 * mentre i pod non satuarano mai per ipotesi.
			 */
			bandwidth_per_pod[server/ (SERV_ON_RACK * RACK_ON_POD)]+=currentBand;
			
		}
		
		double tor_switch_power_consumption = 0;
		for (int i = 0; i < RACK_NUM; ++i) {
			if(bandwidth_per_rack[i]!=0){
			tor_switch_power_consumption += P_TS_IDLE
					+ (P_TS_PEAK - P_TS_IDLE)
					* (bandwidth_per_rack[i] / (SERV_ON_RACK * SERVER_LINK_CAPACITY[i]));
			}
		}
		
		double agg_switch_power_consumption = 0;
		for (int i = 0; i < POD_NUM; ++i) {
			if(bandwidth_per_pod[i]!=0){
				/*
				 * La formula per il calcolo della potenza degli AGG Switch che sfruttano
				 * la politica del load balancing è
				 * P_COPPIA= 2 * [(P_PEAK - P_IDLE)* BW/2 + P_IDLE]
				 * che corrisponde a
				 * P_COPPIA = (P_PEAK - P_IDLE)* BW + 2 P_IDLE
				 */
			agg_switch_power_consumption += (2*P_AGG_IDLE
					+ (P_AGG_PEAK - P_AGG_IDLE)
					* (bandwidth_per_pod[i] / (RACK_ON_POD * RACK_LINK_CAPACITY[i])));
			}
		}

		switch_power_consumption = tor_switch_power_consumption
				+ agg_switch_power_consumption;
		
		
		double maxExecutionTime=-1;
		double totalPowerConsumption=0;
		
		double maxCpuUsage=-1;
		double maxRamUsage=-1;
		double maxDiskUsage=-1;
		
		double minCpuUsage=Double.MAX_VALUE;
		double minRamUsage=Double.MAX_VALUE;
		double minDiskUsage=Double.MAX_VALUE;
		
		
		for(int i=0; i< SERV_NUM; ++i){
			cpu[i]=serverAllocation[i].getCpuRequest();
			double time=serverAllocation[i].executionTime();
			if(time>maxExecutionTime){
				maxExecutionTime=time;
			}
			if(serverAllocation[i].getCpuConstraint()>0){
				serverCPUconstraint+=serverAllocation[i].getCpuConstraint();
				violatedCPUconstraint++;
			}
			
			/* SE SI CONSIDERA LA CPU, BISOGNA IN EQUAL MODO CONSIDERARE LE ALTRE RISORSE
			if(cpu[i]>CPU[i]){
				/*
				 * Se viene allocata una cpu maggiore di quella richiesta, ad ogni VM verrà assegnata una porzione di
				 * potenza di calcolo proporzionalmente minore pari a
				 * CPU_PERCENTAGE / (CPU_REQUIRED) che corrisponde a
				 *  1 / cpu[i];
				 */
			/*
				
				serverExecutionTime[i]*=cpu[i];
				
				serverCPUconstraint+=cpu[i]-1;
				cpu[i]=1;
			}
			
			
		*/	
			/*
			 * Constraint violation 
			 */			
			if(cpu[i]>maxCPU[i]){
				serverCPUconstraint+=cpu[i]-maxCPU[i];
				cpu[i]=maxCPU[i];
				violatedCPUconstraint++;				
			}
		
			if(memory[i]>maxMEMORY[i]){
				serverMEMconstraint+=memory[i]-maxMEMORY[i];
				memory[i]=maxMEMORY[i];
				violatedMEMconstraint++;
			}
			
			if(disk[i]>maxDISK[i]){
				serverDISKconstraint+=disk[i]-maxDISK[i];
				disk[i]=maxDISK[i];
				violatedDISKconstraint++;				
			}
			//End constraint
			
		//Objectives
			//MAX AUSILIARY
			if (maxAusiliaryObj) {

				if (cpu[i] > maxCpuUsage) {
					maxCpuUsage = cpu[i];
				}

				if (memory[i] > maxRamUsage) {
					maxRamUsage = memory[i];
				}

				if (disk[i] > maxDiskUsage) {
					maxDiskUsage = disk[i];
				}

			}//END MAX AUSILIARY OBJ
			
			
			//START MIN AUSILIARY OBJ
			if (minAusiliaryObj) {

				if (cpu[i] < minCpuUsage) {
					minCpuUsage = cpu[i];
				}

				if (memory[i] < minRamUsage) {
					minRamUsage = memory[i];
				}

				if (disk[i] < minDiskUsage) {
					minDiskUsage = disk[i];
				}
			}
			//END MIN AUSILIARY OBJ

			//// Fitness Function 1
			if(maxExecutionTime < serverExecutionTime[i]){
				maxExecutionTime=serverExecutionTime[i];
			}
			////Fitness Function 2
			server_power_consumption+= (P_S_PEAK[i] -P_S_IDLE[i])*cpu[i] + P_S_IDLE[i];
			////
			
			
			
			
		}
		
		totalPowerConsumption=switch_power_consumption+server_power_consumption;
		
		solution.setObjective(0, maxExecutionTime);
		solution.setObjective(1, totalPowerConsumption);
		int i=2;
		if(maxAusiliaryObj){
				solution.setObjective(i, maxCpuUsage);
				i++;
				solution.setObjective(i, maxRamUsage);
				i++;
				solution.setObjective(i, maxDiskUsage);
				i++;
		}
		if(minAusiliaryObj){

			solution.setObjective(i, -minCpuUsage);
			i++;
			solution.setObjective(i, -minRamUsage);
			i++;
			solution.setObjective(i, -minDiskUsage);
			i++;
		}
		if(constrObj){
			solution.setObjective(i, serverCPUconstraint);
			i++;
			solution.setObjective(i, serverMEMconstraint);
			i++;
			solution.setObjective(i, serverDISKconstraint);
			i++;
			
		}
	}
		public void evaluateConstraints(Solution solution) throws JMException {
		int number_violated_constraints = 0;
		number_violated_constraints+=violatedCPUconstraint+violatedDISKconstraint+violatedMEMconstraint;
		if (serverBWconstraint > 0)
			number_violated_constraints++;
		if (rackBWconstraint > 0)
			number_violated_constraints++;
		solution.setOverallConstraintViolation(rackBWconstraint
				+ serverBWconstraint+serverMEMconstraint+serverCPUconstraint);
		solution.setNumberOfViolatedConstraint(number_violated_constraints);
				
	}
	
}
