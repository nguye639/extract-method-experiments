package org.jetbrains.research.extractMethodsReloaded.extractors;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.GitVcs;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.jetbrains.research.extractMethodsReloaded.utils.PsiUtil.vcsSetup;

/**
 * Runs RefactoringMiner and processes discovered "Extract Method" refactorings in project's changes history.
 */
public class PositiveRefactoringsExtractionRunner {
    private final List<String> repositoriesPaths;
    private final FileWriter fileWriter;
    private final Logger LOG = LogManager.getLogger(PositiveRefactoringsExtractionRunner.class);

    public PositiveRefactoringsExtractionRunner(List<String> repositoryPaths, FileWriter fw) {
        this.repositoriesPaths = repositoryPaths;
        this.fileWriter = fw;
    }

    public void run() {
        for (String repoPath : repositoriesPaths) {
            LOG.info("[RefactoringJudge]: Processing repo at: " + repoPath);
            try {
                collectSamples(repoPath);
            } catch (Exception e) {
                LOG.error("[RefactoringJudge]: Could not parse repository: " + repoPath);
            }
        }
        try {
            this.fileWriter.close();
        } catch (IOException e) {
            LOG.error("[RefactoringJudge]: Cannot close the file-writer.");
        }
        LOG.info("[RefactoringJudge]: Finished positive extraction");
    }

    private void collectSamples(String projectPath) {
        Project project = ProjectUtil.openOrImport(projectPath, null, true);

        if (project == null) {
            LOG.error("[RefactoringJudge]: Could not open project " + projectPath);
            return;
        }

        GitRepositoryManager gitRepoManager = ServiceManager.getService(project, GitRepositoryManager.class);
        ProjectLevelVcsManagerImpl vcsManager = vcsSetup(project, projectPath);
        VirtualFile[] gitRoots = vcsManager.getRootsUnderVcs(GitVcs.getInstance(project));
        for (VirtualFile root : gitRoots) {
            GitRepository repo = gitRepoManager.getRepositoryForRoot(root);
            if (repo != null) {
                try {
                    List<GitCommit> gitCommits = GitHistoryUtils.history(project, root, "--all");
                    gitCommits.forEach(c -> processCommit(c, project));
                } catch (VcsException e) {
                    LOG.error("[RefactoringJudge]: Error occurred while processing commit in " + projectPath);
                }
            }
        }
    }

    private void processCommit(GitCommit commit, Project project) {
        GitService gitService = new GitServiceImpl();
        Repository repository = null;
        try {
            repository = gitService.openRepository(project.getBasePath());
        } catch (Exception e) {
            LOG.error("[RefactoringJudge]: Error occurred while opening git repository.");
        }
        GitHistoryRefactoringMiner refactoringMiner = new GitHistoryRefactoringMinerImpl();
        refactoringMiner.detectAtCommit(repository, commit.getId().asString(),
                new CustomRefactoringHandler(project,
                        project.getProjectFilePath().replace(".idea/misc.xml", ""),
                        commit, fileWriter));
    }

}
