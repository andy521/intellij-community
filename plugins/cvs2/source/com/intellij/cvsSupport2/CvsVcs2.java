package com.intellij.cvsSupport2;


import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.merge.CvsMergeProvider;
import com.intellij.cvsSupport2.annotate.CvsAnnotationProvider;
import com.intellij.cvsSupport2.annotate.CvsFileAnnotation;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsStorageComponent;
import com.intellij.cvsSupport2.changeBrowser.CvsCommittedChangesProvider;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinEnvironment;
import com.intellij.cvsSupport2.checkinProject.CvsRollbackEnvironment;
import com.intellij.cvsSupport2.checkout.CvsCheckoutProvider;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditOptionsDialog;
import com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class intended to be an adapter of  AbstractVcs and ProjectComponent interfaces for CVS
 *
 * @author pavel
 * @author lesya
 */

public class CvsVcs2 extends AbstractVcs implements TransactionProvider, EditFileProvider, CvsEntriesListener {

  private final Cvs2Configurable myConfigurable;

  @NonNls private static final String ourRevisionPattern = "\\d+(\\.\\d+)*";

  private CvsStorageComponent myStorageComponent = CvsStorageComponent.ABSENT_STORAGE;
  private final CvsHistoryProvider myCvsHistoryProvider;
  private final CvsCheckinEnvironment myCvsCheckinEnvironment;
  private final CvsCheckoutProvider myCvsCheckoutProvider;
  
  private RollbackEnvironment myCvsRollbackEnvironment;
  private final CvsStandardOperationsProvider myCvsStandardOperationsProvider;
  private final CvsUpdateEnvironment myCvsUpdateEnvironment;
  private final CvsStatusEnvironment myCvsStatusEnvironment;
  private final CvsAnnotationProvider myCvsAnnotationProvider;
  private final CvsDiffProvider myDiffProvider;
  private final CvsCommittedChangesProvider myCommittedChangesProvider;
  private final VcsShowSettingOption myAddOptions;
  private final VcsShowSettingOption myRemoveOptions;
  private final VcsShowSettingOption myCheckoutOptions;
  private final VcsShowSettingOption myEditOption;

  private final VcsShowConfirmationOption myAddConfirmation;
  private final VcsShowConfirmationOption myRemoveConfirmation;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;

  public CvsVcs2(Project project, CvsStorageComponent cvsStorageComponent) {
    super(project);
    myCvsHistoryProvider = new CvsHistoryProvider(project);
    myCvsCheckinEnvironment = new CvsCheckinEnvironment(getProject());
    myCvsCheckoutProvider = new CvsCheckoutProvider();
    myCvsStandardOperationsProvider = new CvsStandardOperationsProvider(project);
    myCvsUpdateEnvironment = new CvsUpdateEnvironment(project);
    myCvsStatusEnvironment = new CvsStatusEnvironment(myProject);

    myConfigurable = new Cvs2Configurable(getProject());
    myStorageComponent = cvsStorageComponent;
    myCvsAnnotationProvider = new CvsAnnotationProvider(myProject);
    myDiffProvider = new CvsDiffProvider(myProject);
    myCommittedChangesProvider = new CvsCommittedChangesProvider(myProject);

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myAddOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myRemoveOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);
    myEditOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.EDIT, this);

    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    myRemoveConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
  }

  /* ======================================= ProjectComponent */

  public Project getProject() {
    return myProject;
  }

  /* ======================================== AbstractVcs*/
  public String getName() {
    return "CVS";
  }

  public String getDisplayName() {
    return CvsBundle.getCvsDisplayName();
  }

  public Configurable getConfigurable() {
    return myConfigurable;
  }


  public TransactionProvider getTransactionProvider() {
    return this;
  }

  public void startTransaction(Object parameters) throws VcsException {
    myCvsStandardOperationsProvider.createTransaction();
  }

  public void commitTransaction(Object parameters) throws VcsException {
    myCvsStandardOperationsProvider.commit(parameters);
  }

  public void rollbackTransaction(Object parameters) {
    myCvsStandardOperationsProvider.rollback();
  }


  public byte[] getFileContent(String path) throws VcsException {
    return myCvsStandardOperationsProvider.getFileContent(path);
  }

  public CvsStandardOperationsProvider getStandardOperationsProvider() {
    return myCvsStandardOperationsProvider;
  }
  /* =========================================================*/


  public static CvsVcs2 getInstance(Project project) {
    return (CvsVcs2) ProjectLevelVcsManager.getInstance(project).findVcsByName("CVS");
  }

  public int getFilesToProcessCount() {
    return myCvsStandardOperationsProvider.getFilesToProcessCount();
  }

  public static void executeOperation(String title, CvsOperation operation, final Project project) throws VcsException {
    CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
    CvsResult result = executor.getResult();
    if (!result.hasNoErrors()) {
      throw result.composeError();
    }
  }

  public static CvsOperationExecutor executeQuietOperation(String title, CvsOperation operation, final Project project) {
    CvsOperationExecutor executor = new CvsOperationExecutor(false, project, ModalityState.defaultModalityState());
    executor.setIsQuietOperation(true);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
    return executor;
  }

  public VcsShowSettingOption getAddOptions() {
    return myAddOptions;
  }

  public VcsShowSettingOption getRemoveOptions() {
    return myRemoveOptions;
  }

  public VcsShowSettingOption getCheckoutOptions() {
    return myCheckoutOptions;
  }

  public EditFileProvider getEditFileProvider() {
    return this;
  }

  public void editFiles(final VirtualFile[] files) {
    if (getEditOptions().getValue()) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(myProject);
      editOptionsDialog.show();
      if (!editOptionsDialog.isOK()) return;
    }

    final CvsHandler editHandler = CommandCvsHandler.createEditHandler(files, CvsConfiguration.getInstance(myProject).RESERVED_EDIT);
    new CvsOperationExecutor(true, myProject, ModalityState.current()).performActionSync(editHandler, CvsOperationExecutorCallback.EMPTY);

  }

  public String getRequestText() {
    return CvsBundle.message("message.text.edit.file.request");
  }

  public ChangeProvider getChangeProvider() {
    if (myChangeProvider == null) {
      myChangeProvider = new CvsChangeProvider(this, CvsEntriesManager.getInstance());
    }
    return myChangeProvider;
  }

  public void activate() {
    super.activate();
    myStorageComponent.init(getProject(), false);
    CvsEntriesManager.getInstance().addCvsEntriesListener(this);
  }

  public void deactivate() {
    super.deactivate();
    myStorageComponent.dispose();
    CvsEntriesManager.getInstance().removeCvsEntriesListener(this);
  }

  public void entriesChanged(VirtualFile parent) {
    VirtualFile[] children = parent.getChildren();
    if (children == null) return;
    for (VirtualFile child : children) {
      fireFileStatusChanged(child);
    }

    VcsDirtyScopeManager.getInstance(getProject()).fileDirty(parent);
  }

  public void entryChanged(VirtualFile file) {
    fireFileStatusChanged(file);
    VcsDirtyScopeManager.getInstance(getProject()).fileDirty(file);
  }

  private void fireFileStatusChanged(final VirtualFile file) {
    FileStatusManager.getInstance(getProject()).fileStatusChanged(file);
  }

  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    return myCvsCheckinEnvironment;
  }

  public RollbackEnvironment getRollbackEnvironment() {
    if (myCvsRollbackEnvironment == null) {
      myCvsRollbackEnvironment = new CvsRollbackEnvironment(myProject);
    }
    return myCvsRollbackEnvironment;
  }

  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return myCvsHistoryProvider;
  }

  @NotNull
  public VcsHistoryProvider getVcsHistoryProvider() {
    return myCvsHistoryProvider;
  }

  public String getMenuItemText() {
    return CvsBundle.message("menu.text.cvsGroup");
  }

  public UpdateEnvironment getUpdateEnvironment() {
    return myCvsUpdateEnvironment;
  }

  public boolean fileIsUnderVcs(FilePath filePath) {
    return CvsUtil.fileIsUnderCvs(filePath.getIOFile());
  }

  public boolean fileExistsInVcs(FilePath path) {
    return CvsUtil.fileExistsInCvs(path);
  }

  public UpdateEnvironment getStatusEnvironment() {
    return myCvsStatusEnvironment;
  }

  public AnnotationProvider getAnnotationProvider() {
    return myCvsAnnotationProvider;
  }

  private static class RevisionPresentation implements VcsFileRevision {
    private final VcsRevisionNumber myNumber;
    private final String myAuthor;
    private final Date myDate;

    private RevisionPresentation(final String revision, final String author, final Date date) {
      myNumber = new CvsRevisionNumber(revision);
      myAuthor = author;
      myDate = date;
    }

    public VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }

    public String getBranchName() {
      return null;
    }

    public Date getRevisionDate() {
      return myDate;
    }

    public String getAuthor() {
      return myAuthor;
    }

    public String getCommitMessage() {
      return null;
    }

    public void loadContent() throws VcsException {
    }

    public byte[] getContent() throws IOException {
      return new byte[0];
    }
  }

  public FileAnnotation createAnnotation(VirtualFile cvsVirtualFile, String revision, CvsEnvironment environment) throws VcsException {
    // the VirtualFile has a full path if annotate is called from history (when we have a real file on disk),
    // and has the path equal to a CVS module name if annotate is called from the CVS repository browser
    // (when there's no real path)
    boolean hasLocalFile = false;
    File cvsFile = new File(cvsVirtualFile.getPath());
    if (cvsFile.isAbsolute()) {
      hasLocalFile = true;
      cvsFile = new File(CvsUtil.getModuleName(cvsVirtualFile));
    }
    final AnnotateOperation annotateOperation = new AnnotateOperation(cvsFile, revision, environment);
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), annotateOperation),
                               CvsOperationExecutorCallback.EMPTY);

    if (executor.getResult().hasNoErrors()) {
      final List<VcsFileRevision> revisions;
      if (hasLocalFile) {
        final CvsHistoryProvider historyProvider = (CvsHistoryProvider)getVcsHistoryProvider();
        final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(cvsVirtualFile);
        revisions = historyProvider.createRevisions(filePath);
      }
      else {
        // imitation
        final Annotation[] lineAnnotations = annotateOperation.getLineAnnotations();
        revisions = new ArrayList<VcsFileRevision>();
        final Set<String> usedRevisions = new HashSet<String>();
        for (Annotation annotation : lineAnnotations) {
          if (! usedRevisions.contains(annotation.getRevision())) {
            revisions.add(new RevisionPresentation(annotation.getRevision(), annotation.getUserName(), annotation.getDate()));
            usedRevisions.add(annotation.getRevision());
          }
        }
      }
      return new CvsFileAnnotation(annotateOperation.getContent(), annotateOperation.getLineAnnotations(), revisions, cvsVirtualFile);
    }
    else {
      throw executor.getResult().composeError();
    }

  }

  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  public VcsShowSettingOption getEditOptions() {
    return myEditOption;
  }

  public VcsShowConfirmationOption getAddConfirmation() {
    return myAddConfirmation;
  }

  public VcsShowConfirmationOption getRemoveConfirmation() {
    return myRemoveConfirmation;
  }

  @Nullable
  public RevisionSelector getRevisionSelector() {
    return new CvsRevisionSelector(myProject);
  }

  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangesProvider;
  }

  @Override @Nullable
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    return new CvsRevisionNumber(revisionNumberString);
  }

  @Override
  public String getRevisionPattern() {
    return ourRevisionPattern;
  }

  public static String staticRevisionPattern() {
    return ourRevisionPattern;
  }

  @Override
  public ThreeStateBoolean isVersionedDirectory(final VirtualFile dir) {
    final VirtualFile child = dir.findChild("CVS");
    return ThreeStateBoolean.getInstance(child != null && child.isDirectory());
  }

  public CvsCheckoutProvider getCheckoutProvider() {
    return myCvsCheckoutProvider;
  }

  @Override
  public MergeProvider getMergeProvider() {
    if (myMergeProvider != null) {
      myMergeProvider = new CvsMergeProvider();
    }
    return myMergeProvider;
  }
}

