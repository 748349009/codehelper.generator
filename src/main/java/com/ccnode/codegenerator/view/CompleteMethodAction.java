package com.ccnode.codegenerator.view;

import com.ccnode.codegenerator.genCode.genFind.ParseJpaContext;
import com.ccnode.codegenerator.genCode.genFind.ParseJpaStrService;
import com.ccnode.codegenerator.pojo.OnePojoInfo;
import com.ccnode.codegenerator.pojoHelper.OnePojoInfoHelper;
import com.ccnode.codegenerator.util.DocumentUtil;
import com.ccnode.codegenerator.util.LoggerWrapper;
import com.ccnode.codegenerator.util.PsiDocumentUtils;
import com.ccnode.codegenerator.util.PsiElementUtil;
import com.google.common.base.Throwables;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * What always stop you is what you always believe.
 * <p>
 * Created by zhengjun.du on 2017/08/13 20:36
 */
public class CompleteMethodAction extends PsiElementBaseIntentionAction {
    private final static Logger LOGGER = LoggerWrapper.getLogger(CompleteMethodAction.class);

    public static final String COMPLETE_METHOD = "Complete Method";
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        long startTime = System.currentTimeMillis();
        try {
            VirtualFileManager.getInstance().syncRefresh();
            LOGGER.info("invoke start ");
            PsiElement parent = element.getParent();
            TextRange textRange = null;
            String methodName = StringUtils.EMPTY;
            if (parent instanceof PsiMethod) {
                return;
            } else if (parent instanceof PsiJavaCodeReferenceElement) {
                methodName = parent.getText();
                textRange = parent.getTextRange();
            } else if (element instanceof PsiWhiteSpace) {
                PsiElement lastMatchedElement = findLastMatchedElement(element);
                methodName = lastMatchedElement.getText();
                textRange = lastMatchedElement.getTextRange();
            }
            PsiClass containingClass = PsiElementUtil.getContainingClass(element);
            if(containingClass == null){
                return;
            }
            OnePojoInfo onePojoInfo = OnePojoInfoHelper.parseOnePojoInfoFromClass(containingClass, project);
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            Document javaDocument = psiDocumentManager.getDocument(containingClass.getContainingFile());
            Document xmlDocument = psiDocumentManager.getDocument(onePojoInfo.getXmlFile());
            ParseJpaContext context = ParseJpaStrService.parse(methodName, xmlDocument, onePojoInfo);
            if(!context.isSuccess()){
                Messages.showMessageDialog(project, context.getErrorMessage(), "Failure", null);
                return;
            }
            PsiDocumentUtils.commitAndSaveDocument(project, javaDocument, textRange, context.getDaoMethodText());
            LOGGER.info("CompleteMethodAction save dao cost :{}", System.currentTimeMillis() - startTime);
//            Thread.sleep(1000);
            Integer line = DocumentUtil.locateLineNumber(xmlDocument, "</mapper>");
            LOGGER.info("invoke line :{}", line);
            LOGGER.info("invoke context.getXmlMethodText() :{}", context.getXmlMethodText());
            if(line > 0) {
                TextRange range = new TextRange(xmlDocument.getLineStartOffset(line), xmlDocument.getLineStartOffset(line));
                String insertText = context.getXmlMethodText();
                if(StringUtils.isNotBlank(DocumentUtil.getTextByLine(xmlDocument, line -1))){
                    insertText = "\n" + insertText;
                }
                PsiDocumentUtils.commitAndSaveDocument(project, xmlDocument, range, insertText);
                LOGGER.info("CompleteMethodAction save xml cost :{}", System.currentTimeMillis() - startTime);
                CodeInsightUtil.positionCursor(project, onePojoInfo.getXmlFile(), onePojoInfo.getXmlFile().findElementAt(xmlDocument.getLineStartOffset(line)));
            }
            PsiDocumentUtils.appendMethodToXml(project, onePojoInfo.getDaoClass(), context.getXmlMethodText());
            PsiDocumentUtils.appendMethodToClass(project, onePojoInfo.getDaoClass(), context.getDaoMethodText());
            PsiDocumentUtils.appendMethodToClass(project, onePojoInfo.getServiceClass(), context.getServiceMethodText());

        } catch (Throwable e) {
            LOGGER.error("CompleteMethodAction invoke error, {}", e);
            Messages.showMessageDialog(project, Throwables.getStackTraceAsString(e), "Plugin Crash", null);
        } finally {
            LOGGER.info("CompleteMethodAction invoke cost :{}", System.currentTimeMillis() - startTime);
            LoggerWrapper.saveAllLogs(project.getBasePath());
            VirtualFileManager.getInstance().syncRefresh();
        }
    }


    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        if (!isAvailableForElement(element)) {
            return false;
        }
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        assert containingClass != null;
        PsiElement leftBrace = containingClass.getLBrace();
        if (leftBrace == null) {
            return false;
        }
        if (element instanceof PsiMethod) {
            return false;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethod) {
            return false;
        }
        if (element instanceof PsiWhiteSpace) {
            PsiElement element1 = findLastMatchedElement(element);
            if (element1 == null) {
                return false;
            }
            return true;
        }
        if (parent instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) parent;
            String text = referenceElement.getText().toLowerCase();
            if (MethodNameCompletionContributor.checkValidTextStarter(text)) {
                return true;
            }
        }
        return false;
    }

    private PsiElement findLastMatchedElement(PsiElement element) {
        PsiElement prevSibling = element.getPrevSibling();
        while (prevSibling != null && isIgnoreText(prevSibling.getText())) {
            prevSibling = prevSibling.getPrevSibling();
        }
        if (prevSibling != null) {
            String lowerCase = prevSibling.getText().toLowerCase();
            if (MethodNameCompletionContributor.checkValidTextStarter(lowerCase)) {
                return prevSibling;
            }
        }
        return null;
    }

    private boolean isIgnoreText(String text) {
        return (text.equals("")) || (text.equals("\n")) || text.equals(" ");
    }

    @NotNull
    @Override
    public String getText() {
        return COMPLETE_METHOD;
    }

    public static boolean isAvailableForElement(PsiElement psiElement) {
        if (psiElement == null) {
            return false;
        }

        PsiClass containingClass = PsiElementUtil.getContainingClass(psiElement);
        if (containingClass == null) {
            return false;
        }
        Module srcMoudle = ModuleUtilCore.findModuleForPsiElement(containingClass);
        if (srcMoudle == null) {
            return false;
        }
        if (containingClass.isAnnotationType() || containingClass instanceof PsiAnonymousClass || !containingClass.isInterface()) {
            return false;
        }
        return true;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return COMPLETE_METHOD;
    }
}
