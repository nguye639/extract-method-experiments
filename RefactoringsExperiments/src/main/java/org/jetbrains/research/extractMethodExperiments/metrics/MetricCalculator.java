package org.jetbrains.research.extractMethodExperiments.metrics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.extractMethodExperiments.features.Feature;
import org.jetbrains.research.extractMethodExperiments.features.FeatureItem;
import org.jetbrains.research.extractMethodExperiments.features.FeaturesVector;
import org.jetbrains.research.extractMethodExperiments.utils.MemberSets;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static org.jetbrains.research.extractMethodExperiments.metrics.DepthAnalyzer.getNestingArea;

public class MetricCalculator {
    private static final Logger LOG = Logger.getInstance(MetricCalculator.class);
    private final PsiMethod psiMethod;
    private final int beginLine;
    private final int endLine;
    private FeaturesVector featuresVector;

    public MetricCalculator(PsiMethod psiMethod, int beginLine, int endLine) {
        this.psiMethod = psiMethod;
        this.beginLine = beginLine;
        this.endLine = endLine;
        this.featuresVector = new FeaturesVector(82); // TODO: Make dimension changeable outside
        computeFeatureVector();
    }

    private void computeFeatureVector() {
        couplingFeatures();
        keywordFeatures();
        methodFeatures();
        metaFeatures();
        historicalFeatures();
    }

    private static Repository openRepository(String repositoryPath) throws Exception {
        File folder = new File(repositoryPath);
        Repository repository;
        if (folder.exists()) {
            RepositoryBuilder builder = new RepositoryBuilder();
            repository = builder
                    .setGitDir(new File(folder, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
        } else {
            throw new FileNotFoundException(repositoryPath);
        }
        return repository;
    }

    public FeaturesVector getFeaturesVector() {
        return this.featuresVector;
    }

    private void couplingFeatures() {
        PsiFile thisFile = psiMethod.getContainingFile();
        MemberSets memberSets = MemberSets.extractAllMethodsAndFields(thisFile);

        int linesCount = endLine - beginLine + 1;

        int fieldMatches = 0;
        int methodMatches = 0;
        int totalMatches;

        PsiFileFactory factory = PsiFileFactory.getInstance(thisFile.getProject());
       // @Nullable PsiFile psiFromText = factory.createFileFromText(psiMethod, thisFile);

        // search for all identifiers (methods and variables) in the code fragment
        @NotNull Collection<PsiIdentifier> identifiers = PsiTreeUtil.collectElementsOfType(psiMethod,
                PsiIdentifier.class);
        HashSet<String> identifiersNames = new HashSet<>();
        identifiers.forEach(i -> identifiersNames.add(i.getText()));

        for (String fieldName : memberSets.fields) {
            if (identifiersNames.contains(fieldName)) {
                fieldMatches += 1;
            }
        }

        for (String methodName : memberSets.methods) {
            if (identifiersNames.contains(methodName)) {
                methodMatches += 1;
            }
        }

        totalMatches = methodMatches + fieldMatches;

        featuresVector.addFeature(new FeatureItem(Feature.TotalConnectivity, totalMatches));
        featuresVector.addFeature(new FeatureItem(
                Feature.TotalConnectivityPerLine, (double) totalMatches / linesCount));
        featuresVector.addFeature(new FeatureItem(Feature.FieldConnectivity, fieldMatches));
        featuresVector.addFeature(new FeatureItem(
                Feature.FieldConnectivityPerLine, (double) fieldMatches / linesCount));
        featuresVector.addFeature(new FeatureItem(Feature.MethodConnectivity, methodMatches));
        featuresVector.addFeature(new FeatureItem(
                Feature.MethodConnectivityPerLine, (double) methodMatches / linesCount));
    }

    private void keywordFeatures() {
        List<String> allKeywords = Arrays.asList(
                "continue", "for", "new", "switch",
                "assert", "synchronized", "boolean", "do",
                "if", "this", "break", "double",
                "throw", "byte", "else", "case",
                "instanceof", "return", "transient",
                "catch", "int", "short", "try",
                "char", "final", "finally", "long",
                "strictfp", "float", "super", "while"); // 31 keywords, id from 20 to 81

        HashMap<String, Integer> counts = new HashMap<>();
        for (String key : allKeywords) {
            counts.put(key, StringUtils.countMatches(psiMethod.getText(), key));
        }

        int linesCount = endLine - beginLine + 1;

        int id = 20; // initialized with 20 to account for shift in Keyword-Features begin id.
        for (String keyword : allKeywords) {
            Integer count = counts.get(keyword);
            featuresVector.addFeature(new FeatureItem(Feature.fromId(id++), count));
            featuresVector.addFeature(new FeatureItem(Feature.fromId(id++), (double) count / linesCount));
        }
    }

    private void methodFeatures() {
        String method = this.psiMethod.getText();
        int methodArea = getNestingArea(method);
        int lineCount = StringUtils.countMatches(method, '\n') + 1;

        featuresVector.addFeature(new FeatureItem(Feature.MethodDeclarationLines, lineCount));
        featuresVector.addFeature(new FeatureItem(Feature.MethodDeclarationSymbols, method.length()));
        featuresVector.addFeature(new FeatureItem(
                Feature.MethodDeclarationSymbolsPerLine, (double) method.length() / lineCount));
        featuresVector.addFeature(new FeatureItem(Feature.MethodDeclarationArea, methodArea));
        featuresVector.addFeature(new FeatureItem(
                Feature.MethodDeclarationAreaPerLine, (double) methodArea / lineCount));

    }

    private void metaFeatures() {
        String fragment = psiMethod.getText();
        int fragmentArea = getNestingArea(fragment);
        int lineCount = StringUtils.countMatches(fragment, '\n') + 1;

        featuresVector.addFeature(new FeatureItem(Feature.TotalLinesOfCode, lineCount));
        featuresVector.addFeature(new FeatureItem(Feature.TotalSymbols, fragment.length()));
        featuresVector.addFeature(new FeatureItem(
                Feature.SymbolsPerLine, (double) fragment.length() / lineCount));
        featuresVector.addFeature(new FeatureItem(Feature.Area, fragmentArea));
        featuresVector.addFeature(new FeatureItem(
                Feature.AreaPerLine, (double) fragmentArea / lineCount));

    }

    private void historicalFeatures() { //Actually no clue if it works
        String repoPath = psiMethod.getContainingFile().getProject().getBasePath();
        String filePath = psiMethod.getContainingFile().getVirtualFile().getCanonicalPath();
        Repository repository;
        try {
            repository = openRepository(repoPath);
        } catch (Exception e) {
            LOG.error("Failed to open the project repository.");
            return;
        }

        BlameResult result = null;
        try {
            result = new Git(repository).blame().setFilePath
                    (filePath.substring(filePath.indexOf("src")))
                    .setTextComparator(RawTextComparator.WS_IGNORE_ALL).call();
        } catch (GitAPIException e) {
            LOG.error("Failed to get GitBlame.");
        }

        ArrayList<Integer> creationDates = new ArrayList<>();
        Set<String> commits = new HashSet<>();
        Set<String> authors = new HashSet<>();
        if (result != null) {
            final RawText rawText = result.getResultContents();
            for (int i = beginLine; i < Math.min(rawText.size(), endLine + 1); i++) {
                final PersonIdent sourceAuthor = result.getSourceAuthor(i);
                final RevCommit sourceCommit = result.getSourceCommit(i);
                if (sourceCommit != null) {
                    creationDates.add(sourceCommit.getCommitTime());
                    commits.add(sourceCommit.getName());
                    authors.add(sourceAuthor.getName());
                }
            }
        }

        featuresVector.addFeature(
                new FeatureItem(Feature.TotalCommitsInFragment, commits.size()));
        featuresVector.addFeature(
                new FeatureItem(Feature.TotalAuthorsInFragment, authors.size()));

        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;

        for (Integer time : creationDates) {
            if (minTime > time) {
                minTime = time;
            }
            if (maxTime < time) {
                maxTime = time;
            }
        }

        if (minTime != Integer.MAX_VALUE) {
            int totalTime = 0;
            for (Integer time : creationDates) {
                totalTime += time - minTime;
            }

            featuresVector.addFeature(new FeatureItem(Feature.LiveTimeOfFragment, maxTime - minTime));
            featuresVector.addFeature(
                    new FeatureItem(Feature.LiveTimePerLine, (double) totalTime / creationDates.size()));
        }
    }
}