export class DbLoadResponseDto {

  isDbLoaded: string;
  lastLoad: string;
  status: string;
  dbLoadSteps: Array<DbLoadSteps>;
  fileCounts?: { [key: string]: number };  // ftpData, unzipedFiles, inputFiles

}

export class DbLoadSteps {
  stepName: string;
  startTime: string;
  stepEndTime: string;
  readCount: string;
  writeCount: string;
  stepStatus: string;
  exitMessage?: string;  // Spring-Batch EXIT_MESSAGE (Stack-Trace) bei FAILED-Steps
}
