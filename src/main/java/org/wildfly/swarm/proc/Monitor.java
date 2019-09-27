/*
 * *
 *  * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.swarm.proc;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.github.zafarkhaja.semver.Version;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hyperic.sigar.ProcMem;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.ptql.ProcessFinder;

import static org.wildfly.swarm.proc.Units.bytesToMegabytes;

/**
 * @author Heiko Braun
 * @since 28/04/16
 */
public class Monitor {

    public Monitor(CommandLine cmd) {
        skipTests = cmd.hasOption("skip");

        baseDir = new File(cmd.getOptionValue("b"));
        workDir = new File(cmd.getOptionValue("w"));
        archiveDir = cmd.hasOption("a") ? Optional.of(new File(cmd.getOptionValue("a"))) : Optional.empty();

        outputFile = cmd.hasOption("o") ? Optional.of(new File(cmd.getOptionValue("o"))) : Optional.empty();
        comparisonOutputFile = cmd.hasOption("c") ? Optional.of(new File(cmd.getOptionValue("c"))) : Optional.empty();

        System.out.println("Base dir: "+ baseDir.getAbsolutePath());

        if(archiveDir.isPresent())
            System.out.println("Archive dir: "+ archiveDir.get().getAbsolutePath());

        if(archiveDir.isPresent() && !archiveDir.get().exists())
            throw new RuntimeException("Archive does not exist: "+archiveDir.get().getAbsolutePath());

        collector = (outputFile.isPresent() && !skipTests) ?
                new CSVCollector(outputFile.get()) : new SystemOutCollector();


        this.NUM_ITERATIONS = cmd.hasOption("n") ? Integer.valueOf(cmd.getOptionValue("n")) : 10;
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption(Option.builder("b")
                .longOpt("base")
                .required(true)
                .desc("the WildFly Swarm examples directory")
                .hasArg()
                .build()
        );

        options.addOption(Option.builder("a")
                .longOpt("archive")
                .required(false)
                .desc("the directory with previous performance results")
                .hasArg()
                .build()
        );


        options.addOption(Option.builder("o")
                .longOpt("output")
                .required(false)
                .desc("the .csv file to store the current test results")
                .hasArg()
                .build()
        );

        options.addOption(Option.builder("skip")
                .longOpt("skip-tests")
                .required(false)
                .desc("skip test execution phase")
                .build()
        );

        options.addOption(Option.builder("n")
                .longOpt("number-iterations")
                .required(false)
                .hasArg()
                .desc("number of iterations per test")
                .build()
        );

        options.addOption(Option.builder("w")
                .longOpt("workdir")
                .required(true)
                .hasArg()
                .desc("where to store testing artifacts")
                .build()
        );

        options.addOption(Option.builder("c")
                .longOpt("comparison-csv")
                .required(false)
                .hasArg()
                .desc("the .csv file to store the comparison")
                .build()
        );

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            usage(options);
        }

        for(Option opt : options.getOptions())
        {
            if(opt.isRequired() && !cmd.hasOption(opt.getOpt()))
            {
                usage(options);
            }
        }

        // perform tests
        new Monitor(cmd).run();
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Monitor", "WildFly Swarm Performance Monitor", options, "", true);
        System.exit(-1);
    }

    private void run() throws Exception {
        long total0 = System.currentTimeMillis();

        if(!skipTests) {
            // test criteria
            Properties props = new Properties();
            props.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-apps.properties"));

            Properties argsProps = new Properties();
            argsProps.load(Monitor.class.getClassLoader().getResourceAsStream("swarm-app-args.properties"));

            // first phase: main test execution loop
            for (Object o : props.keySet()) {
                String swarmFile = (String) o;
                String httpCheck = (String) props.get(o);
                String swarmArgs = (String) argsProps.get(o);

                File file = new File(this.baseDir, swarmFile);
                String id = file.getAbsolutePath();

                if (!file.exists())
                    throw new RuntimeException("File does not exist: " + file.getAbsolutePath());

                List<String> argsList = swarmArgs == null
                        ? Collections.emptyList()
                        : Arrays.asList(swarmArgs.split("\\s+"));

                collector.onBegin(id);
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    runTest(i, file, httpCheck, argsList, collector);
                }
                collector.onFinish(id);
            }
            collector.close();

            System.out.println("Test Execution Time: " + (System.currentTimeMillis() - total0) + "ms");

        }
        else {
            System.out.println("Test execution has been skipped.");
        }


        // second phase: compare with previous, archived results
        if(outputFile.isPresent() && archiveDir.isPresent()) {
            Optional<ArchivedResult> prev = getPreviousResults(outputFile.get().toPath(), this.archiveDir.get());
            if (prev.isPresent()) {
                // maybe we should check here that outputFile is a valid CSV (in case we skipped the tests and are running
                // against an already existing file), but if it isn't, things will fail down the line anyway
                checkDeviation(this.outputFile.get(), prev.get());
            } else {
                System.out.println("Performance comparison skipped.");
            }
        }
    }

    private void checkDeviation(File testResult, ArchivedResult archivedResult) throws Exception {
        System.out.println("Comparing against " + archivedResult.getVersion());

        List<CSVRecord> current = loadCSV(testResult).getRecords();
        List<CSVRecord> previous = loadCSV(archivedResult.getFile()).getRecords();

        if (comparisonOutputFile.isPresent()) {
            String previousName = archivedResult.getVersion().toString();
            String currentName = testResult.getName().replaceFirst(".csv$", "");
            new CsvOutputComparator(comparisonOutputFile.get(), previousName, currentName).compare(previous, current);
        }

        new FailFastComparator(10.00).compare(previous, current);
    }

    private CSVParser loadCSV(File file) throws Exception {
        Reader input = Files.newBufferedReader(file.toPath());
        return CSVFormat.DEFAULT.withHeader().parse(input);
    }

    private static boolean isSameFile(Path path1, Path path2) {
        try {
            return Files.isSameFile(path1, path2);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<ArchivedResult> getPreviousResults(Path currentOutput, File dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir.toPath(), 1)) {
            return stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .filter(path -> !isSameFile(currentOutput, path))
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        Version version = Version.valueOf(fileName.substring(0, fileName.lastIndexOf(".")));
                        return new ArchivedResult(version, path.toFile());
                    })
                    .sorted(Comparator.comparing(ArchivedResult::getVersion).reversed())
                    .findFirst();
        }
    }

    /**
     * Main test execution. Spawns an external process
     * @param iteration
     * @param file
     * @param httpCheck
     * @param processArgs
     * @param collector
     */
    private void runTest(int iteration, File file, String httpCheck, List<String> processArgs, final Collector collector) {

        System.out.println("Testing " + file.getAbsolutePath() + ", iteration " + iteration);
        String id = file.getAbsolutePath();

        String uid = UUID.randomUUID().toString();
        Process process = null;
        int attempts = 0;

        try {
            Path workDir = Files.createDirectories(this.workDir.toPath().resolve(Paths.get(file.getName(), "iteration-" + iteration)));
            Path tmp = Files.createDirectory(workDir.resolve("tmp"));

            List<String> command = new ArrayList<>();
            command.addAll(Arrays.asList("java",
                                         "-Duid=" + uid,
                                         "-Djava.io.tmpdir=" + tmp.toAbsolutePath().toString(),
                                         "-jar", file.getAbsolutePath())
            );
            command.addAll(processArgs);
            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectOutput(workDir.resolve("stdout.txt").toFile())
                    .redirectError(workDir.resolve("stderr.txt").toFile());

            final long s0 = System.currentTimeMillis();
            process = pb.start();

            final CloseableHttpClient httpClient = HttpClients.createDefault();

            while (true) {
                if (attempts >= NUM_CONNECTION_ATTEMPTS) {
                    System.out.println("Max attempts reached, escaping sequence");
                    break;
                }

                CloseableHttpResponse response = null;
                try {
                    HttpGet request = new HttpGet(httpCheck);
                    response = httpClient.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 200) {
                        collector.onMeasurement(id, Measure.STARTUP_TIME, (double) (System.currentTimeMillis() - s0));
                        warmup(httpClient, httpCheck);
                        measureMemory(id, uid, collector);
                        measureJarSize(id, file, collector);
                        measureTmpDirSize(id, tmp, collector);
                        break;
                    } else if (statusCode == 404) {
                        // this can happen during server boot, when the HTTP endpoint is already exposed
                        // but the application is not yet deployed
                    } else {
                        System.err.println("Failed to execute HTTP check: " + statusCode);
                        break;
                    }
                } catch (HttpHostConnectException e) {
                    // server not running yet
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }

                attempts++;
                Thread.sleep(MS_BETWEEN_ATTEMPTS);
            }

            httpClient.close();

            final long s1 = System.currentTimeMillis();
            process.destroy();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (finished) {
                collector.onMeasurement(id, Measure.SHUTDOWN_TIME, (double) (System.currentTimeMillis() - s1));
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (process!=null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void warmup(CloseableHttpClient httpClient, String httpCheck) throws IOException {
        for (int i = 0; i < 100; i++) {
            HttpGet request = new HttpGet(httpCheck);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    System.err.println("Failed to execute warmup: " + statusCode);
                    return;
                }
            }
        }
    }

    private void measureMemory(String id, String uid, Collector collector) throws Exception {
        // see https://support.hyperic.com/display/SIGAR/PTQL
        Sigar sigar = new Sigar();
        final ProcessFinder processFinder = new ProcessFinder(sigar);
        long pid = processFinder.findSingleProcess("State.Name.eq=java,Args.1.ct="+uid);

        for (int i = 0; i < 10; i++) {
            Jcmd.gc(pid);
        }

        ProcMem procMem = sigar.getProcMem(pid);
        long rss = procMem.getResident();
        collector.onMeasurement(id, Measure.RSS_AFTER_INVOCATION, bytesToMegabytes(rss));

        long javaHeap = Jstat.usedHeap(pid);
        collector.onMeasurement(id, Measure.JAVA_HEAP_AFTER_INVOCATION, bytesToMegabytes(javaHeap));
    }

    private void measureJarSize(String id, File jar, Collector collector) throws IOException {
        long jarSize = jar.length();
        collector.onMeasurement(id, Measure.JAR_SIZE, bytesToMegabytes(jarSize));
    }

    private void measureTmpDirSize(String id, Path tmpDir, Collector collector) throws IOException {
        try (Stream<Path> stream = Files.walk(tmpDir)) {
            long tmpDirSize = stream
                    .mapToLong(path -> path.toFile().length())
                    .sum();

            collector.onMeasurement(id, Measure.TMP_DIR_SIZE, bytesToMegabytes(tmpDirSize));
        }
    }

    private static final int NUM_CONNECTION_ATTEMPTS = 1000;

    private static final int MS_BETWEEN_ATTEMPTS = 20;

    private int NUM_ITERATIONS = 10;

    private final File baseDir;

    private final File workDir;

    private final Optional<File> archiveDir;

    private final Optional<File> outputFile;

    private final Optional<File> comparisonOutputFile;

    private final Collector collector;

    private boolean skipTests;


    static class ArchivedResult {
        private Version version;
        private File file;

        public ArchivedResult(Version version, File file) {
            this.version = version;
            this.file = file;
        }

        public Version getVersion() {
            return version;
        }

        public File getFile() {
            return file;
        }
    }
}
