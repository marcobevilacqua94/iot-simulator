package org.ingestor;

import com.couchbase.client.core.retry.BestEffortRetryStrategy;
import com.couchbase.client.java.*;
import com.couchbase.client.java.query.QueryResult;
import org.apache.commons.cli.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
public class Main {

    public static void main(String[] args) {

        DocGenerator docGenerator = new DLDocGenerator();

        /* parameters to use if no command line is found */
        String username = "Administrator";
        String password = "password";
        String ip = "127.0.0.1";
        String bucketName = "sample";
        String scopeName = "_default";
        String collectionName = "_default";
        int buffer = 1000;
        int docs = 1000000000;
        long contentLimit = 0L;


        CommandLine commandLine;
        Option option_h = Option.builder("h").argName("host").hasArg().desc("couchbase ip").build();
        Option option_u = Option.builder("u").argName("username").hasArg().desc("couchbase username").build();
        Option option_p = Option.builder("p").argName("password").hasArg().desc("couchbase password").build();
        Option option_b = Option.builder("b").argName("bucket").hasArg().desc("couchbase bucket").build();
        Option option_f = Option.builder("f").argName("buffer").hasArg().desc("buffer").build();
        Option option_s = Option.builder("s").argName("scope").hasArg().desc("couchbase scope").build();
        Option option_c = Option.builder("c").argName("collection").hasArg().desc("couchbase collection").build();
        Option option_docs = Option.builder("n").argName("num-of-docs").hasArg().desc("docs to create").build();
        Option option_content_limit = Option.builder("cl").argName("content-limit").hasArg().desc("content limit number of the bucket").build();

        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption(option_h);
        options.addOption(option_u);
        options.addOption(option_p);
        options.addOption(option_docs);
        options.addOption(option_b);
        options.addOption(option_c);
        options.addOption(option_s);
        options.addOption(option_f);
        options.addOption(option_content_limit);

        String header = "               [<arg1> [<arg2> [<arg3> ...\n       Options, flags and arguments may be in any order";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("CLIsample", header, options, null, true);


        try
        {
            commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h"))
            {
                System.out.printf("host ip: %s%n", commandLine.getOptionValue("h"));
                ip = commandLine.getOptionValue("h");
            }

            if (commandLine.hasOption("u"))
            {
                System.out.printf("couchbase username: %s%n", commandLine.getOptionValue("u"));
                username = commandLine.getOptionValue("u");
            }

            if (commandLine.hasOption("p"))
            {
                System.out.printf("couchbase password: %s%n", commandLine.getOptionValue("p"));
                password = commandLine.getOptionValue("p");
            }

            if (commandLine.hasOption("n"))
            {
                System.out.printf("docs to save: %s%n", commandLine.getOptionValue("n"));
                docs = Integer.parseInt(commandLine.getOptionValue("n"));
            }
            if (commandLine.hasOption("b"))
            {
                System.out.printf("couchbase bucket: %s%n", commandLine.getOptionValue("b"));
                bucketName = commandLine.getOptionValue("b");
            }
            if (commandLine.hasOption("s"))
            {
                System.out.printf("couchbase scope: %s%n", commandLine.getOptionValue("s"));
                scopeName = commandLine.getOptionValue("s");
            }
            if (commandLine.hasOption("c"))
            {
                System.out.printf("couchbase collection: %s%n", commandLine.getOptionValue("c"));
                collectionName = commandLine.getOptionValue("c");
            }
            if (commandLine.hasOption("f"))
            {
                System.out.printf("buffer: %s%n", commandLine.getOptionValue("f"));
                buffer = Integer.parseInt(commandLine.getOptionValue("f"));
            }
            if (commandLine.hasOption("cl"))
            {
                System.out.printf("content limit: %s%n", commandLine.getOptionValue("cl"));
                contentLimit = Long.parseLong(commandLine.getOptionValue("cl"));
            }
        }
        catch (ParseException exception)
        {
            System.out.print("Parse error: ");
            System.out.println(exception.getMessage());
        }

        try(
                Cluster cluster = Cluster.connect(
                        ip,
                        ClusterOptions.clusterOptions(username, password).environment(env -> {
                            env.retryStrategy(BestEffortRetryStrategy.withExponentialBackoff(Duration.ofNanos(1000),
                                    Duration.ofMillis(1), 2));
                            // Customize client settings by calling methods on the "env" variable.
                        })
                )) {

            ReactiveBucket bucket = cluster.bucket(bucketName).reactive();
            ReactiveScope scope = bucket.scope(scopeName);
            ReactiveCollection collection = scope.collection(collectionName);
            long finalContentLimit = contentLimit;
            String query = "select COUNT(*) as count from `" + bucketName + "`.`" + scopeName + "`.`" + collectionName + "`";
            Flux.range(1, docs).buffer(buffer)
                    .map(counterList -> {
                                if (finalContentLimit > 0) {
                                    QueryResult result = cluster.query(query);
                                    if(Long.parseLong(result.rowsAsObject().get(0).get("count").toString()) >= finalContentLimit) {
                                        System.exit(0);
                                    }
                                }
                                return Flux.fromIterable(counterList).flatMap(counter ->
                                        collection.upsert(UUID.randomUUID().toString(),
                                                docGenerator.generateDoc(counter))).count().single().block();
                            }
                    )
                    .count()
                    .single()
                    .block();
        }
    }



}