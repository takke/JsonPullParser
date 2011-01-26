/*
 * Copyright 2011 vvakame <vvakame@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.vvakame.util.jsonpullparser.factory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.JavaFileObject;

import static net.vvakame.apt.AptUtil.*;
import net.vvakame.util.jsonpullparser.annotation.JsonKey;
import net.vvakame.util.jsonpullparser.annotation.JsonModel;
import net.vvakame.util.jsonpullparser.factory.JsonElement.Kind;
import net.vvakame.util.jsonpullparser.factory.template.Template;
import net.vvakame.util.jsonpullparser.util.TokenConverter;

public class ClassGenerateHelper {
	static ProcessingEnvironment processingEnv = null;
	static String postfix = "";

	GeneratingModel g = new GeneratingModel();
	Element classElement;

	boolean encountError = false;

	public static void init(ProcessingEnvironment env) {
		processingEnv = env;
	}

	public static ClassGenerateHelper newInstance(Element element) {
		return new ClassGenerateHelper(element);
	}

	public ClassGenerateHelper(Element element) {
		classElement = element;

		g.setPackageName(getPackageName(element));
		g.setTarget(getSimpleName(element));
		g.setPostfix(postfix);
		g.setTreatUnknownKeyAsError(getTreatUnknownKeyAsError(element));
	}

	public void addElement(Element element) {
		JsonElement jsonElement = element.asType().accept(
				new ValueExtractVisitor(), element);
		g.addJsonElement(jsonElement);
	}

	public void write() throws IOException {

		Filer filer = processingEnv.getFiler();
		String generateClassName = classElement.asType().toString() + postfix;
		JavaFileObject fileObject = filer.createSourceFile(generateClassName,
				classElement);
		Template.write(fileObject, g);
	}

	public void process() {
		// JsonKeyの収集
		List<Element> elements = getEnclosedElementsByAnnotation(classElement,
				JsonKey.class, ElementKind.FIELD);
		if (elements.size() == 0) {
			Log.e("not exists any @JsonKey decorated field.", classElement);
		}

		// JsonKeyに対応する値取得コードを生成する
		for (Element element : elements) {
			addElement(element);
		}
	}

	String getElementKeyString(Element element) {
		JsonKey key = element.getAnnotation(JsonKey.class);
		return "".equals(key.value()) ? element.toString() : key.value();
	}

	String getConverterClassName(Element el) {

		AnnotationValue converter = null;

		for (AnnotationMirror am : el.getAnnotationMirrors()) {
			Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = am
					.getElementValues();
			for (ExecutableElement e : elementValues.keySet()) {
				if ("converter".equals(e.getSimpleName().toString())) {
					converter = elementValues.get(e);
				}
			}
		}

		String result = null;
		if (converter != null
				&& !TokenConverter.class.getCanonicalName().equals(converter)) {
			String tmp = converter.toString();
			if (tmp.endsWith(".class")) {
				int i = tmp.lastIndexOf('.');
				result = tmp.substring(0, i);
			} else {
				result = tmp;
			}
		}

		return result;
	}

	boolean getTreatUnknownKeyAsError(Element element) {
		JsonModel model = element.getAnnotation(JsonModel.class);
		if (model == null) {
			throw new IllegalArgumentException();
		}
		return model.treatUnknownKeyAsError();
	}

	class ValueExtractVisitor extends
			StandardTypeKindVisitor<JsonElement, Element> {

		JsonElement genJsonElement(TypeMirror t, Element el, Kind kind) {
			if (kind == null) {
				Log.e("invalid state. this is APT bugs.");
				encountError = true;
				return defaultAction(t, el);
			}

			JsonElement jsonElement = new JsonElement();
			jsonElement.setKey(getElementKeyString(el));

			String setter = getElementSetter(el);
			if (setter == null) {
				Log.e("can't find setter method", el);
				encountError = true;
				return defaultAction(t, el);
			}

			String converterClassName = getConverterClassName(el);
			if (converterClassName != null) {
				TypeElement element = processingEnv.getElementUtils()
						.getTypeElement(converterClassName);
				Log.d(element.asType().toString());
				if (element == null
						|| !isMethodExists(element, "getInstance",
								Modifier.PUBLIC, Modifier.STATIC)) {
					Log.e("converter needs [public static getInstance()].",
							element);
				}
				kind = Kind.CONVERTER;
			}

			jsonElement.setSetter(setter);
			jsonElement.setModelName(t.toString());
			jsonElement.setKind(kind);
			jsonElement.setConverter(converterClassName);

			return jsonElement;
		}

		@Override
		public JsonElement visitPrimitiveAsBoolean(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.BOOLEAN);
		}

		@Override
		public JsonElement visitPrimitiveAsByte(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.BYTE);
		}

		@Override
		public JsonElement visitPrimitiveAsChar(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.CHAR);
		}

		@Override
		public JsonElement visitPrimitiveAsDouble(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.DOUBLE);
		}

		@Override
		public JsonElement visitPrimitiveAsFloat(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.FLOAT);
		}

		@Override
		public JsonElement visitPrimitiveAsInt(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.INT);
		}

		@Override
		public JsonElement visitPrimitiveAsLong(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.LONG);
		}

		@Override
		public JsonElement visitPrimitiveAsShort(PrimitiveType t, Element el) {
			return genJsonElement(t, el, Kind.SHORT);
		}

		@Override
		public JsonElement visitString(DeclaredType t, Element el) {
			return genJsonElement(t, el, Kind.STRING);
		}

		@Override
		public JsonElement visitList(DeclaredType t, Element el) {

			JsonElement jsonElement;

			String converterClassName = getConverterClassName(el);
			if (converterClassName != null) {
				jsonElement = genJsonElement(t, el, Kind.CONVERTER);

			} else {

				List<? extends TypeMirror> generics = t.getTypeArguments();
				if (generics.size() != 1) {
					Log.e("expected single type generics.", el);
					encountError = true;
					return defaultAction(t, el);
				}
				TypeMirror tm = generics.get(0);
				if (tm instanceof WildcardType) {
					WildcardType wt = (WildcardType) tm;
					TypeMirror extendsBound = wt.getExtendsBound();
					if (extendsBound != null) {
						tm = extendsBound;
					}
					TypeMirror superBound = wt.getSuperBound();
					if (superBound != null) {
						tm = superBound;
					}
				}

				Element type = processingEnv.getTypeUtils().asElement(tm);
				JsonModel hash = type.getAnnotation(JsonModel.class);
				if (hash == null) {
					Log.e("expect for use decorated class by JsonModel annotation.",
							el);
					encountError = true;
					return defaultAction(t, el);
				}

				jsonElement = new JsonElement();
				jsonElement.setKey(getElementKeyString(el));

				String setter = getElementSetter(el);
				if (setter == null) {
					Log.e("can't find setter method", el);
					encountError = true;
					return defaultAction(t, el);
				}
				jsonElement.setSetter(setter);
				jsonElement.setModelName(tm.toString());
				jsonElement.setKind(Kind.LIST);
			}

			return jsonElement;
		}

		@Override
		public JsonElement visitJsonHash(DeclaredType t, Element el) {
			return genJsonElement(t, el, Kind.JSON_HASH);
		}

		@Override
		public JsonElement visitJsonArray(DeclaredType t, Element el) {
			return genJsonElement(t, el, Kind.JSON_ARRAY);
		}

		@Override
		public JsonElement visitUndefinedClass(DeclaredType t, Element el) {

			TypeMirror tm = t.asElement().asType();
			Element type = processingEnv.getTypeUtils().asElement(tm);
			JsonModel hash = type.getAnnotation(JsonModel.class);
			if (hash == null) {
				Log.e("expect for use decorated class by JsonHash annotation.",
						el);
				encountError = true;
				return defaultAction(t, el);
			}

			return genJsonElement(t, el, Kind.MODEL);
		}
	}

	/**
	 * @param postfix
	 *            the postfix to set
	 */
	public static void setPostfix(String postfix) {
		ClassGenerateHelper.postfix = postfix;
	}

	/**
	 * @return the encountError
	 */
	public boolean isEncountError() {
		return encountError;
	}
}
