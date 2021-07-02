package ru.hse.kirilenko.refactorings;

import ru.hse.kirilenko.refactorings.extractors.ExtractionConfig;
import ru.hse.kirilenko.refactorings.extractors.ExtractionRunner;
import ru.hse.kirilenko.refactorings.csv.SparseCSVBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class TrueRefactoringsExtractorCaller {
    public static void run(String path){
        File file = new File(path);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            List<String> repos = new ArrayList<>();
            br.lines().forEach(s -> repos.add(s));

            SparseCSVBuilder.sharedInstance = new SparseCSVBuilder("true.csv", ExtractionConfig.nFeatures);
            ExtractionRunner runner = new ExtractionRunner(repos);
            new Thread(() -> runner.run()).start();

        } catch (Exception e) {
            String errormsg = String.format("Warning, there is no such file: %s\nExiting...", path);
            System.out.println(errormsg);
            System.exit(0);
        }
    }
}
