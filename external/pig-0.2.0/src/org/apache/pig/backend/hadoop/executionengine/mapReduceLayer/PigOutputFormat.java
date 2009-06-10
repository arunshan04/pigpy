/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.backend.hadoop.executionengine.mapReduceLayer;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;
import org.apache.pig.PigException;
import org.apache.pig.StoreFunc;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.builtin.PigStorage;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.tools.bzip2r.CBZip2OutputStream;

/**
 * The better half of PigInputFormat which is responsible
 * for the Store functionality. It is the exact mirror
 * image of PigInputFormat having RecordWriter instead
 * of a RecordReader.
 */
public class PigOutputFormat implements OutputFormat<WritableComparable, Tuple> {
    public static final String PIG_OUTPUT_FUNC = "pig.output.func";

    public RecordWriter<WritableComparable, Tuple> getRecordWriter(FileSystem fs, JobConf job,
            String name, Progressable progress) throws IOException {
        Path outputDir = FileOutputFormat.getWorkOutputPath(job);
        return getRecordWriter(fs, job, outputDir, name, progress);
    }

    public PigRecordWriter getRecordWriter(FileSystem fs, JobConf job,
            Path outputDir, String name, Progressable progress)
            throws IOException {
        StoreFunc store;
        String storeFunc = job.get("pig.storeFunc", "");
        if (storeFunc.length() == 0) {
            store = new PigStorage();
        } else {
            try {
                store = (StoreFunc) PigContext
                        .instantiateFuncFromSpec(storeFunc);
            } catch (Exception e) {
                int errCode = 2081;
                String msg = "Unable to setup the store function.";
                throw new ExecException(msg, errCode, PigException.BUG, e);
            }
        }

        String parentName = FileOutputFormat.getOutputPath(job).getName();
        int suffixStart = parentName.lastIndexOf('.');
        if (suffixStart != -1) {
            String suffix = parentName.substring(suffixStart);
            if (suffix.equals(".bz") || suffix.equals(".bz2")) {
                name = name + suffix;
            }
        }
        return new PigRecordWriter(fs, new Path(outputDir, name), store);
    }

    public void checkOutputSpecs(FileSystem fs, JobConf job) throws IOException {
        // TODO We really should validate things here
        return;
    }

    static public class PigRecordWriter implements
            RecordWriter<WritableComparable, Tuple> {
        private OutputStream os = null;

        private StoreFunc sfunc = null;

        public PigRecordWriter(FileSystem fs, Path file, StoreFunc sfunc)
                throws IOException {
            this.sfunc = sfunc;
            fs.delete(file, true);
            this.os = fs.create(file);
            String name = file.getName();
            if (name.endsWith(".bz") || name.endsWith(".bz2")) {
                os = new CBZip2OutputStream(os);
            }
            this.sfunc.bindTo(os);
        }

        /**
         * We only care about the values, so we are going to skip the keys when
         * we write.
         * 
         * @see org.apache.hadoop.mapred.RecordWriter#write(K, V)
         */
        public void write(WritableComparable key, Tuple value)
                throws IOException {
            this.sfunc.putNext(value);
        }

        public void close(Reporter reporter) throws IOException {
            sfunc.finish();
            os.close();
        }

    }
}
