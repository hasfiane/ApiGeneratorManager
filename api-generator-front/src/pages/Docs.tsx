import { ChangeEvent, type ComponentProps } from 'react'
import { Shell } from '../components/Shell'
import { Icon } from '../components/Icon'
import { useLanguage } from '../i18n/LanguageProvider'
import { docsContent } from '../content/docsContent'

type DocSection = {
  id: string
  title: string
  summary: string
  whenTitle: string
  whenItems: readonly string[]
  beforeTitle: string
  beforeItems: readonly string[]
  stepsTitle: string
  steps: readonly string[]
  screenshot: string
  nextTitle: string
  nextItems: readonly string[]
  tipsTitle: string
  tips: readonly string[]
  errorsTitle: string
  errors: ReadonlyArray<{
    title: string
    seen: string
    why: string
    fix: string
  }>
  faqTitle: string
  faq: ReadonlyArray<{
    question: string
    answer: string
  }>
}

type DocsCopy = {
  title: string
  subtitle: string
  action: string
  overviewTitle: string
  overviewText: string
  overviewCards: readonly (readonly [string, string, string])[]
  summaryTitle: string
  summaryText: string
  summarySelectLabel: string
  summaryPlaceholder: string
  quickSummaryLabel: string
  errorSeenLabel: string
  errorWhyLabel: string
  errorFixLabel: string
  sections: readonly DocSection[]
}

export default function Docs() {
  const { locale } = useLanguage()
  const t = docsContent[locale] as unknown as DocsCopy
  const sections = t.sections
  const technologies: ReadonlyArray<readonly [ComponentProps<typeof Icon>['name'], string]> = [
    ['spring', 'Spring Boot'],
    ['postgres', 'PostgreSQL'],
    ['mysql', 'MySQL'],
    ['h2', 'H2'],
    ['docker', 'Docker'],
    ['swagger', 'Swagger'],
    ['jdbc', 'JDBC'],
    ['jwt', 'JWT'],
    ['oauth', 'OAuth'],
    ['nginx', 'Nginx'],
  ]

  const jumpToSection = (event: ChangeEvent<HTMLSelectElement>) => {
    const sectionId = event.target.value
    if (!sectionId) return
    window.location.hash = sectionId
  }

  return (
    <Shell
      title={t.title}
      subtitle={t.subtitle}
      actions={<a className="btn primary" href="/app/generators">{t.action}</a>}
    >
      <div className="card docLandingCard">
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.overviewTitle}</h3>
            <p className="panelText">{t.overviewText}</p>
          </div>
        </div>
        <div className="docGuideGrid">
          {t.overviewCards.map(([title, description, href]) => (
            <a key={href} className="docGuideCard" href={`#${href}`}>
              <strong>{title}</strong>
              <p>{description}</p>
            </a>
          ))}
        </div>
      </div>

      <div style={{ height: 16 }} />
      <div className="card docLandingCard">
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.summaryTitle}</h3>
            <p className="panelText">{t.summaryText}</p>
          </div>
        </div>
        <label className="field">
          <span>{t.summarySelectLabel}</span>
          <select defaultValue="" onChange={jumpToSection}>
            <option value="" disabled>{t.summaryPlaceholder}</option>
            {sections.map((section) => (
              <option key={section.id} value={section.id}>{section.title}</option>
            ))}
          </select>
        </label>
      </div>

      <div style={{ height: 16 }} />
      <div className="card docLandingCard">
        <div className="docTechGrid">
          {technologies.map(([icon, label]) => (
            <div key={label} className="techBadge">
              <span className="techBadgeIcon"><Icon name={icon} size={16} /></span>
              <span>{label}</span>
            </div>
          ))}
        </div>
      </div>

      {sections.map((section) => (
        <div key={section.id}>
          <div style={{ height: 16 }} />
          <section id={section.id} className="docSection">
            <div className="card">
              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle">{section.title}</h3>
                  <p className="panelText">{section.summary}</p>
                </div>
              </div>

              <div className="docSectionGrid">
                <div className="docColumn">
                  <div className="docBlock">
                    <h4>{t.quickSummaryLabel}</h4>
                    <p>{section.summary}</p>
                  </div>

                  <div className="docBlock">
                    <h4>{section.whenTitle}</h4>
                    <ul className="docList">
                      {section.whenItems.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>

                  <div className="docBlock">
                    <h4>{section.beforeTitle}</h4>
                    <ul className="docList">
                      {section.beforeItems.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                </div>

                <div className="docColumn">
                  <div className="docBlock">
                    <h4>{section.stepsTitle}</h4>
                    <ol className="docNumbered">
                      {section.steps.map((item) => (
                        <li key={item}>
                          <span className="docStepMark" aria-hidden="true">v</span>
                          <span>{item}</span>
                        </li>
                      ))}
                    </ol>
                    <div className="callout docScreenshotHint">{section.screenshot}</div>
                  </div>

                  <div className="docBlock">
                    <h4>{section.nextTitle}</h4>
                    <ul className="docList">
                      {section.nextItems.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>

                  <div className="docBlock">
                    <h4>{section.tipsTitle}</h4>
                    <ul className="docList">
                      {section.tips.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                </div>
              </div>

              <div className="docSubsection">
                <h4>{section.errorsTitle}</h4>
                <div className="docErrorGrid">
                  {section.errors.map((error) => (
                    <div className="docErrorCard" key={error.title}>
                      <strong>{error.title}</strong>
                      <p><span>{t.errorSeenLabel}</span> {error.seen}</p>
                      <p><span>{t.errorWhyLabel}</span> {error.why}</p>
                      <p><span>{t.errorFixLabel}</span> {error.fix}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="docSubsection">
                <h4>{section.faqTitle}</h4>
                <div className="docSteps">
                  {section.faq.map((item) => (
                    <div className="docStep" key={item.question}>
                      <h4>{item.question}</h4>
                      <p>{item.answer}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </section>
        </div>
      ))}
    </Shell>
  )
}
