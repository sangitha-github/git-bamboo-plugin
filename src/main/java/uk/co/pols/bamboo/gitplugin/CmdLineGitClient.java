package uk.co.pols.bamboo.gitplugin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.repository.RepositoryException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import uk.co.pols.bamboo.gitplugin.commands.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CmdLineGitClient implements GitClient {
    private static final Log log = LogFactory.getLog(CmdLineGitClient.class);
    private String gitExe;

    public CmdLineGitClient(String gitExe) {
        this.gitExe = gitExe;
    }

    public String getLatestUpdate(BuildLogger buildLogger, String repositoryUrl, String planKey, String lastRevisionChecked, List<Commit> commits, File sourceCodeDirectory) throws RepositoryException {
        try {
            pullCommand(sourceCodeDirectory).pullUpdatesFromRemoteRepository(buildLogger, repositoryUrl);

            GitLogCommand gitLogCommand = logCommand(sourceCodeDirectory, lastRevisionChecked);
            List<Commit> gitCommits = gitLogCommand.extractCommits();
            String latestRevisionOnServer = gitLogCommand.getLastRevisionChecked();
            if (lastRevisionChecked == null) {
                log.info("Never checked logs for '" + planKey + "' on path '" + repositoryUrl + "'  setting latest revision to " + latestRevisionOnServer);
                return latestRevisionOnServer;
            }
            if (!latestRevisionOnServer.equals(lastRevisionChecked)) {
                log.info("Collecting changes for '" + planKey + "' on path '" + repositoryUrl + "' since " + lastRevisionChecked);
                commits.addAll(gitCommits);
            }

            return latestRevisionOnServer;
        } catch (IOException e) {
            throw new RepositoryException("Failed to get latest update", e);
        }
    }

    public String initialiseRepository(File sourceCodeDirectory, String planKey, String vcsRevisionKey, GitRepositoryConfig gitRepositoryConfig, boolean isWorkspaceEmpty, BuildLogger buildLogger) throws RepositoryException {
        if (isWorkspaceEmpty) {
            initialiseRemoteRepository(sourceCodeDirectory, gitRepositoryConfig.getRepositoryUrl(), buildLogger);
        }

        return getLatestUpdate(buildLogger, gitRepositoryConfig.getRepositoryUrl(), planKey, vcsRevisionKey, new ArrayList<Commit>(), sourceCodeDirectory);
    }

    protected GitPullCommand pullCommand(File sourceCodeDirectory) {
        return new ExecutorGitPullCommand(gitExe, sourceCodeDirectory, new AntCommandExecutor());
    }

    protected GitLogCommand logCommand(File sourceCodeDirectory, String lastRevisionChecked) {
        // todo this is an executor NOT an Extractor!!!! come back and rename
        return new ExtractorGitLogCommand(gitExe, sourceCodeDirectory, lastRevisionChecked, new AntCommandExecutor());
    }

    protected GitInitCommand initCommand(File sourceCodeDirectory) {
        return new ExecutorGitInitCommand(gitExe, sourceCodeDirectory, new AntCommandExecutor());
    }

    protected GitRemoteCommand remoteCommand(File sourceCodeDirectory) {
        return new ExecutorGitRemoteCommand(gitExe, sourceCodeDirectory, new AntCommandExecutor());
    }

    private void initialiseRemoteRepository(File sourceDirectory, String repositoryUrl, BuildLogger buildLogger) throws RepositoryException {
        log.info(buildLogger.addBuildLogEntry(sourceDirectory.getAbsolutePath() + " is empty. Creating new git repository"));
        try {
            sourceDirectory.mkdirs();
            initCommand(sourceDirectory).init(buildLogger);
            remoteCommand(sourceDirectory).add_origin(repositoryUrl, buildLogger);
        } catch (IOException e) {
            throw new RepositoryException("Failed to initialise repository", e);
        }
    }
}