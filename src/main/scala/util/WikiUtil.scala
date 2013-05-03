package util

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.RepositoryBuilder
import app.DiffInfo
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.diff.DiffEntry.ChangeType

object WikiUtil {
  
  /**
   * The model for wiki page.
   * 
   * @param name the page name
   * @param content the page content
   * @param committer the last committer
   * @param time the last modified time
   */
  case class WikiPageInfo(name: String, content: String, committer: String, time: Date)
  
  /**
   * The model for wiki page history.
   * 
   * @param name the page name
   * @param committer the committer the committer
   * @param message the commit message
   * @param date the commit date
   */
  case class WikiPageHistoryInfo(name: String, committer: String, message: String, date: Date)
  
  /**
   * Returns the directory of the wiki repository.
   */
  def getWikiRepositoryDir(owner: String, repository: String): File =
    new File("%s/%s/%s.wiki.git".format(Directory.RepositoryHome, owner, repository))
  
  /**
   * Returns the directory of the wiki working directory which is cloned from the wiki repository.
   */
  def getWikiWorkDir(owner: String, repository: String): File = 
    new File("%s/tmp/%s/%s.wiki".format(Directory.RepositoryHome, owner, repository))

  // TODO synchronized?
  def createWikiRepository(owner: String, repository: String): Unit = {
    val dir = getWikiRepositoryDir(owner, repository)
    if(!dir.exists){
      val repo = new RepositoryBuilder().setGitDir(dir).setBare.build
      repo.create
      savePage(owner, repository, "Home", "Home", "Welcome to the %s wiki!!".format(repository), owner, "Initial Commit")
    }
  }
  
  /**
   * Returns the wiki page.
   */
  def getPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val git = Git.open(getWikiRepositoryDir(owner, repository))
    try {
      JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
        WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"), file.committer, file.time)
      }
    } catch {
      // TODO no commit, but it should not judge by exception.
      case e: NullPointerException => None
    }
  }
  
  def getPageList(owner: String, repository: String): List[String] = {
    JGitUtil.getFileList(Git.open(getWikiRepositoryDir(owner, repository)), "master", ".")
      .filter(_.name.endsWith(".md"))
      .map(_.name.replaceFirst("\\.md$", ""))
      .sortBy(x => x)
  }
  
  // TODO 
  //def getPageHistory(owner: String, repository: String, pageName: String): List[WikiPageHistoryInfo]
  
  // TODO synchronized
  /**
   * Save the wiki page.
   */
  def savePage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: String, message: String): Unit = {
    
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val workDir = getWikiWorkDir(owner, repository)
    
    // clone
    if(!workDir.exists){
      Git.cloneRepository.setURI(getWikiRepositoryDir(owner, repository).toURI.toString).setDirectory(workDir).call
    }
    
    // write as file
    val cloned = Git.open(workDir)
    val file = new File(workDir, newPageName + ".md")
    val added = if(!file.exists || FileUtils.readFileToString(file, "UTF-8") != content){
      FileUtils.writeStringToFile(file, content, "UTF-8")
      cloned.add.addFilepattern(file.getName).call
      true
    } else {
      false
    }
    
    // delete file
    val deleted = if(currentPageName != "" && currentPageName != newPageName){
      cloned.rm.addFilepattern(currentPageName + ".md")
      true
    } else {
      false
    }
    
    // commit and push
    if(added || deleted){
      cloned.commit.setAuthor(committer, committer + "@devnull").setMessage(message).call
      cloned.push.call
    }
  }
  
  def getDiffs(git: Git, commitId1: String, commitId2: String): List[DiffInfo] = {
//    @scala.annotation.tailrec
//    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[RevCommit]): List[RevCommit] =
//      i.hasNext match {
//        case true if(logs.size < 2) => getCommitLog(i, logs :+ i.next)
//        case _ => logs
//      }
//    
//    val revWalk = new RevWalk(git.getRepository)
//    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(commitId2)))
//    
//    val commits = getCommitLog(revWalk.iterator, Nil)
//    revWalk.release
//    
//    val revCommit = commits(0)
//    
////    if(commits.length >= 2){
//      // not initial commit
//      val oldCommit = commits(1)
      
      // get diff between specified commit and its previous commit
      val reader = git.getRepository.newObjectReader
      
      val oldTreeIter = new CanonicalTreeParser
      oldTreeIter.reset(reader, git.getRepository.resolve(commitId1 + "^{tree}"))
      
      val newTreeIter = new CanonicalTreeParser
      newTreeIter.reset(reader, git.getRepository.resolve(commitId2 + "^{tree}"))
      
      import scala.collection.JavaConverters._
      git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
            JGitUtil.getContent(git, diff.getOldId.toObjectId, false).map(new String(_, "UTF-8")), 
            JGitUtil.getContent(git, diff.getNewId.toObjectId, false).map(new String(_, "UTF-8")))
      }.toList
//    } else {
//      // initial commit
//      val walk = new TreeWalk(git.getRepository)
//      walk.addTree(revCommit.getTree)
//      val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
//      while(walk.next){
//        buffer.append(DiffInfo(ChangeType.ADD, null, walk.getPathString, None, 
//            JGitUtil.getContent(git, walk.getObjectId(0), false).map(new String(_, "UTF-8"))))
//      }
//      walk.release
//      buffer.toList
//    }
  }    
  
}