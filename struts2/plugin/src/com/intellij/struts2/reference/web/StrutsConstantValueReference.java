/*
 * Copyright 2009 The authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.struts2.reference.web;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.javaee.model.xml.ParamValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.struts2.model.constant.StrutsConstantKey;
import com.intellij.struts2.model.constant.StrutsConstantManager;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Delegates {@link com.intellij.struts2.model.constant.StrutsConstant#getConverter()} references.
 *
 * @author Yann C&eacute;bron
 */
class StrutsConstantValueReference extends PsiReferenceBase<XmlTag> implements EmptyResolveMessageProvider {

  @Nullable
  private final Pair<DomElement, Converter> elementConverterPair;

  StrutsConstantValueReference(@NotNull final XmlTag xmlTag) {
    super(xmlTag, false);
    elementConverterPair = getElementConverterPair();
  }

  @SuppressWarnings({"unchecked"})
  public PsiElement resolve() {
    if (elementConverterPair == null) {
      return myElement;
    }

    final Converter converter = elementConverterPair.getSecond();
    final ConvertContext convertContext = AbstractConvertContext.createConvertContext(elementConverterPair.first);

    // additional variants (String only)
    if (converter instanceof ResolvingConverter) {
      final Set additionalVariants = ((ResolvingConverter) converter).getAdditionalVariants(convertContext);
      if (additionalVariants.contains(getValue())) {
        return myElement;
      }
    }

    // "normal" reference
    final Object resolveObject = converter.fromString(getValue(), convertContext);
    if (resolveObject == null) {
      return null;
    }

    // DomElement
    if (resolveObject instanceof DomElement) {
      return ((DomElement) resolveObject).getXmlTag();
    }

    // fake self-reference (e.g. String value from Converter)
    if (!(resolveObject instanceof PsiElement)) {
      return myElement;
    }

    // "real" reference
    return (PsiElement) resolveObject;
  }

  public String getUnresolvedMessagePattern() {
    assert elementConverterPair != null;
    
    return elementConverterPair.second
        .getErrorMessage(getValue(), AbstractConvertContext.createConvertContext(elementConverterPair.first));
  }

  @NotNull
  @SuppressWarnings({"unchecked"})
  public Object[] getVariants() {
    if (elementConverterPair == null) {
      return EMPTY_ARRAY;
    }

    final Converter converter = elementConverterPair.second;
    if (!(converter instanceof ResolvingConverter)) {
      return EMPTY_ARRAY;
    }

    final ResolvingConverter resolvingConverter = (ResolvingConverter) converter;

    // merge "normal" + additional variants
    final DomElement paramValueElement = elementConverterPair.first;
    final ConvertContext convertContext = AbstractConvertContext.createConvertContext(paramValueElement);

    final Collection converterVariants = resolvingConverter.getVariants(convertContext);

    final Collection variants;
    if (!converterVariants.isEmpty() &&
        converterVariants.iterator().next() instanceof DomElement) {
      variants = Arrays.asList(ElementPresentationManager.getInstance().createVariants(converterVariants));
    } else {
      variants = converterVariants;
    }

    variants.addAll(resolvingConverter.getAdditionalVariants(convertContext));

    // add custom created references
    if (resolvingConverter instanceof CustomReferenceConverter) {
      final PsiReference[] references = ((CustomReferenceConverter) resolvingConverter).
          createReferences((GenericDomValue) paramValueElement,
                           myElement,
                           convertContext);
      for (final PsiReference customReference : references) {
        Collections.addAll(variants, customReference.getVariants());
      }
    }

    return variants.toArray(new Object[variants.size()]);
  }

  /**
   * Gets the DomElement and corresponding converter.
   *
   * @return {@code null} on errors or if one of both could not be resolved.
   */
  @Nullable
  private Pair<DomElement, Converter> getElementConverterPair() {
    final DomElement paramValueElement = DomUtil.getDomElement(myElement);
    if (paramValueElement == null) {
      return null;
    }

    final DomElement domElement = paramValueElement.getParent();
    if (!(domElement instanceof ParamValue)) {
      return null;
    }

    final ParamValue initParamElement = (ParamValue) domElement;
    final String paramName = initParamElement.getParamName().getStringValue();
    if (StringUtil.isEmpty(paramName)) {
      return null;
    }

    final StrutsConstantManager constantManager = StrutsConstantManager.getInstance(myElement.getProject());

    @SuppressWarnings({"ConstantConditions"})
    final Converter converter = constantManager.findConverter(myElement, StrutsConstantKey.create(paramName));
    if (converter == null) {
      return null;
    }

    return new Pair<DomElement, Converter>(paramValueElement, converter);
  }

}