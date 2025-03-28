package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.job;

import module java.base;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.Command;

public final class Job {
	private final List<Path> filesToProcess;
	// Map<fileName, Map<stationName, averageValue>>
	private final Map<String, Map<String, Double>> resultMap;
	private final ExecutorService executorService;
	private final List<Future<?>> futures;


	private JobInfo jobInfo;


	public Job(String jobName, Command.Scan scanParams, List<Path> filesToProcess) {
		this.filesToProcess = filesToProcess;
		this.executorService = Executors.newVirtualThreadPerTaskExecutor();
		this.resultMap = new ConcurrentHashMap<>();
		this.futures = new ArrayList<>();

		this.jobInfo = new JobInfo(jobName, scanParams, JobState.PENDING);
	}

	public void start() throws ExecutionException, InterruptedException, IOException {
		this.jobInfo = new JobInfo(this.jobInfo.jobName, this.jobInfo.scanParams, JobState.RUNNING);
		var outputFile = this.jobInfo.scanParams.outputFilename();
		for (Path file : filesToProcess) {
			FileProcessingTask task = new FileProcessingTask(file, this.jobInfo.scanParams, this.jobInfo.jobName);
			Future<?> future = executorService.submit(task);
			futures.add(future);
			for (Future<?> f : futures) {
				f.get();
			}
		}
		this.jobInfo = new JobInfo(this.jobInfo.jobName, this.jobInfo.scanParams, JobState.COMPLETED);
	}
	public void cancel() {
		this.jobInfo = new JobInfo(this.jobInfo.jobName, this.jobInfo.scanParams, JobState.CANCELLED);
		for (Future<?> f : futures) {
			f.cancel(true);
		}
		this.executorService.shutdownNow();
	}

	public JobInfo getJobInfo() {
		return jobInfo;
	}

	public record JobInfo(String jobName, Command.Scan scanParams, JobState state) {}
}